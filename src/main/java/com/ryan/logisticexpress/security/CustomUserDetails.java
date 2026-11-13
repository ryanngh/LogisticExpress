package com.ryan.logisticexpress.security;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String username;
    private final String email;
    private final String password;
    private final Status status;
    private final Integer tokenVersion;
    private final boolean accountLocked;
    private final Set<GrantedAuthority> authorities;

    public CustomUserDetails(
            UUID id,
            String username,
            String email,
            String password,
            Status status,
            Integer tokenVersion,
            boolean accountLocked,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password != null ? password : "";
        this.status = status;
        this.tokenVersion = tokenVersion != null ? tokenVersion : 0;
        this.accountLocked = accountLocked;
        this.authorities = authorities != null ? Set.copyOf(authorities) : Set.of();
    }

    public CustomUserDetails(User user) {
        this(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCredential() != null ? user.getCredential().getPasswordHash() : "",
                user.getStatus(),
                user.getTokenVersion(),
                user.getCredential() != null && user.getCredential().isAccountLocked(),
                user.getRoles() != null
                        ? user.getRoles().stream()
                                .map(Role::name)
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toUnmodifiableSet())
                        : Set.of()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked && status != Status.DISABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == Status.ACTIVE;
    }
}
