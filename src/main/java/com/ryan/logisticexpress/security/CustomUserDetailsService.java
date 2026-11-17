package com.ryan.logisticexpress.security;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    public static final String USER_AUTH_CACHE_PREFIX = "auth:user:state:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    public record UserAuthState(int tokenVersion, Status status, boolean accountLocked) implements Serializable {}

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public CustomUserDetailsService(
            UserRepository userRepository,
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate
    ) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    public CustomUserDetailsService(UserRepository userRepository) {
        this(userRepository, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("Identifier cannot be empty");
        }
        String trimmed = identifier.trim().toLowerCase();
        User user = userRepository.findByIdentifierWithDetails(trimmed, identifier.trim())
                .or(() -> userRepository.findByUsername(trimmed))
                .or(() -> userRepository.findByEmail(trimmed))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

        cacheUserAuthState(user);
        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserById(UUID id) throws UsernameNotFoundException {
        User user = userRepository.findByIdWithDetails(id)
                .or(() -> userRepository.findById(id))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        cacheUserAuthState(user);
        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserForJwt(UUID userId, String username, Collection<String> roles) {
        UserAuthState cached = getCachedAuthState(userId);
        if (cached != null) {
            Set<SimpleGrantedAuthority> authorities = roles != null
                    ? roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toUnmodifiableSet())
                    : Set.of();
            return new CustomUserDetails(
                    userId,
                    username,
                    username,
                    "",
                    cached.status(),
                    cached.tokenVersion(),
                    cached.accountLocked(),
                    authorities
            );
        }
        if (userId != null) {
            var byId = userRepository.findByIdWithDetails(userId).or(() -> userRepository.findById(userId));
            if (byId.isPresent()) {
                User user = byId.get();
                cacheUserAuthState(user);
                return new CustomUserDetails(user);
            }
        }
        return loadUserByUsername(username);
    }

    public UserAuthState getCachedAuthState(UUID userId) {
        if (redisTemplate == null || userId == null) {
            return null;
        }
        try {
            Object raw = redisTemplate.opsForValue().get(USER_AUTH_CACHE_PREFIX + userId);
            if (raw instanceof UserAuthState state) {
                return state;
            }
            if (raw instanceof String str) {
                String[] parts = str.split(":");
                if (parts.length >= 2) {
                    int version = Integer.parseInt(parts[0]);
                    Status status = Status.valueOf(parts[1]);
                    boolean locked = parts.length >= 3 && Boolean.parseBoolean(parts[2]);
                    return new UserAuthState(version, status, locked);
                }
            }
            if (raw instanceof Map<?, ?> map) {
                Object tv = map.get("tokenVersion");
                Object st = map.get("status");
                Object lk = map.get("accountLocked");
                if (tv instanceof Number num && st != null) {
                    return new UserAuthState(
                            num.intValue(),
                            Status.valueOf(st.toString()),
                            lk instanceof Boolean b && b
                    );
                }
            }
        } catch (Exception ex) {
            log.warn("Redis GET failed for user auth state userId={}: {}", userId, ex.getMessage());
        }
        return null;
    }

    public void cacheUserAuthState(User user) {
        if (redisTemplate == null || user == null || user.getId() == null) {
            return;
        }
        try {
            int version = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
            Status status = user.getStatus() != null ? user.getStatus() : Status.ACTIVE;
            boolean locked = user.getCredential() != null && user.getCredential().isAccountLocked();
            String value = version + ":" + status.name() + ":" + locked;
            redisTemplate.opsForValue().set(USER_AUTH_CACHE_PREFIX + user.getId(), value, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Redis SET failed for user auth state userId={}: {}", user.getId(), ex.getMessage());
        }
    }

    public void evictUserAuthState(UUID userId) {
        if (redisTemplate == null || userId == null) {
            return;
        }
        try {
            redisTemplate.delete(USER_AUTH_CACHE_PREFIX + userId);
        } catch (Exception ex) {
            log.warn("Redis DEL failed for user auth state userId={}: {}", userId, ex.getMessage());
        }
    }
}
