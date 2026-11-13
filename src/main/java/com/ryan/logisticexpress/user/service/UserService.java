package com.ryan.logisticexpress.user.service;

import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ForbiddenException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.user.dto.*;
import com.ryan.logisticexpress.user.entity.ShopProfile;
import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.entity.UserCredential;
import com.ryan.logisticexpress.user.entity.UserProfile;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.repository.ShopProfileRepository;
import com.ryan.logisticexpress.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ShopProfileRepository shopProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID currentUserId) {
        User user = findUserById(currentUserId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UUID currentUserId, UpdateUserProfileRequest request) {
        User user = findUserById(currentUserId);

        String trimmedPhone = request.phone().trim();
        userRepository.findByPhone(trimmedPhone)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ConflictException("PHONE_EXISTS", "Phone number is already in use");
                });

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
            user.setProfile(profile);
        }
        profile.setFullName(request.fullName().trim());
        profile.setPhone(trimmedPhone);
        profile.setAvatarUrl(request.avatarUrl() != null ? request.avatarUrl().trim() : null);

        return UserProfileResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public ShopProfileResponse getShopProfile(UUID currentUserId) {
        User user = findUserById(currentUserId);
        if (!user.hasRole(Role.ROLE_SHOP)) {
            throw new ForbiddenException("NOT_A_SHOP", "User does not have shop privileges");
        }

        ShopProfile shopProfile = shopProfileRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("SHOP_NOT_FOUND", "Shop profile not initialized"));
        return ShopProfileResponse.from(shopProfile);
    }

    @Transactional
    public ShopProfileResponse updateShopProfile(UUID currentUserId, UpdateShopProfileRequest request) {
        User user = findUserById(currentUserId);
        if (!user.hasRole(Role.ROLE_SHOP)) {
            throw new ForbiddenException("NOT_A_SHOP", "User does not have shop privileges");
        }

        String shopName = request.shopName().trim();
        shopProfileRepository.findByShopName(shopName)
                .filter(existing -> !existing.getUserId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ConflictException("SHOP_NAME_EXISTS", "Shop name is already taken");
                });

        // Ensure full bank info is supplied if any bank field is present (ck_shop_profiles_bank_complete)
        String bankName = normalize(request.bankName());
        String bankAccountNo = normalize(request.bankAccountNo());
        String bankAccountHolder = normalize(request.bankAccountHolder());
        boolean anyBank = bankName != null || bankAccountNo != null || bankAccountHolder != null;
        boolean allBank = bankName != null && bankAccountNo != null && bankAccountHolder != null;
        if (anyBank && !allBank) {
            throw new BadRequestException("INVALID_BANK_INFO", "Complete bank details required: bank name, account number, and holder");
        }
        if (bankAccountNo != null && !bankAccountNo.matches("^[0-9]{6,20}$")) {
            throw new BadRequestException("INVALID_BANK_ACCOUNT", "Bank account number must be between 6 and 20 digits");
        }

        ShopProfile shopProfile = shopProfileRepository.findById(currentUserId)
                .orElseGet(() -> {
                    ShopProfile sp = ShopProfile.builder().user(user).shopName(shopName).build();
                    user.setShopProfile(sp);
                    return sp;
                });

        shopProfile.setShopName(shopName);
        shopProfile.setPickupAddressId(request.pickupAddressId());
        shopProfile.setBankName(bankName);
        shopProfile.setBankAccountNo(bankAccountNo);
        shopProfile.setBankAccountHolder(bankAccountHolder);

        return ShopProfileResponse.from(shopProfileRepository.save(shopProfile));
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Role role) {
        List<User> staff = (role != null) ? userRepository.findByRole(role) : userRepository.findAllStaff();
        return staff.stream().map(StaffResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaffById(UUID id) {
        return StaffResponse.from(findUserById(id));
    }

    @Transactional
    public StaffResponse createStaff(CreateStaffRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("USERNAME_EXISTS", "Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_EXISTS", "Email already exists");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new ConflictException("PHONE_EXISTS", "Phone number already exists");
        }

        boolean hasStaffRole = request.roles().stream().anyMatch(r -> r != Role.ROLE_SHOP);
        if (!hasStaffRole) {
            throw new BadRequestException("INVALID_ROLE", "Staff user must have at least one internal role");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .status(Status.ACTIVE)
                .roles(request.roles())
                .build();

        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(request.fullName().trim())
                .phone(phone)
                .build();
        user.setProfile(profile);

        UserCredential credential = UserCredential.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(request.password()))
                .mustChangePassword(true)
                .build();
        user.setCredential(credential);

        return StaffResponse.from(userRepository.save(user));
    }

    @Transactional
    public StaffResponse updateStaff(UUID id, UpdateStaffRequest request) {
        User user = findUserById(id);
        String phone = request.phone().trim();

        userRepository.findByPhone(phone)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("PHONE_EXISTS", "Phone number already exists");
                });

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
            user.setProfile(profile);
        }
        profile.setFullName(request.fullName().trim());
        profile.setPhone(phone);

        if (request.roles() != null && !request.roles().isEmpty()) {
            user.setRoles(request.roles());
        }

        if (request.status() != null) {
            user.setStatus(request.status());
            // Invalidate active JWTs when account is disabled
            if (request.status() == Status.DISABLED) {
                user.incrementTokenVersion();
            }
        }

        return StaffResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteStaff(UUID id) {
        User user = findUserById(id);
        // Soft delete: disable and revoke all active tokens
        user.setStatus(Status.DISABLED);
        user.incrementTokenVersion();
        userRepository.save(user);
    }

    private User findUserById(UUID id) {
        if (id == null) {
            throw new BadRequestException("UNAUTHORIZED", "User is unauthenticated");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
