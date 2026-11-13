package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        String phone,
        Status status,
        Set<Role> roles,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static StaffResponse from(User user) {
        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        String phone = user.getProfile() != null ? user.getProfile().getPhone() : null;

        return new StaffResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                fullName,
                phone,
                user.getStatus(),
                user.getRoles(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
