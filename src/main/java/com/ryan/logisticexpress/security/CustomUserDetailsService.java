package com.ryan.logisticexpress.security;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("Identifier cannot be empty");
        }
        String trimmed = identifier.trim().toLowerCase();
        User user = userRepository.findByUsername(trimmed)
                .or(() -> userRepository.findByEmail(trimmed))
                .or(() -> userRepository.findByPhone(identifier.trim()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public CustomUserDetails loadUserById(UUID id) throws UsernameNotFoundException {
        return userRepository.findById(id)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }
}
