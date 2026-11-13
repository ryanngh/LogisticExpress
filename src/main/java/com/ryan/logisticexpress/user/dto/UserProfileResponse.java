package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        Status status,
        Set<Role> roles,
        String fullName,
        String phone,
        String avatarUrl,
        ShopProfileResponse shopProfile,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        String fullName = user.getProfile() != null ? user.getProfile().getFullName() : null;
        String phone = user.getProfile() != null ? user.getProfile().getPhone() : null;
        String avatarUrl = user.getProfile() != null ? user.getProfile().getAvatarUrl() : null;
        ShopProfileResponse shop = user.getShopProfile() != null ? ShopProfileResponse.from(user.getShopProfile()) : null;

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                user.getRoles(),
                fullName,
                phone,
                avatarUrl,
                shop,
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
