package com.ryan.logisticexpress.user.service;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.address.service.AddressService;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ConflictException;
import com.ryan.logisticexpress.common.exception.ForbiddenException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.hub.service.HubOperationService;
import com.ryan.logisticexpress.security.CustomUserDetailsService;
import com.ryan.logisticexpress.user.dto.*;
import com.ryan.logisticexpress.user.entity.ShopProfile;
import com.ryan.logisticexpress.user.entity.StaffProfile;
import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.entity.UserCredential;
import com.ryan.logisticexpress.user.entity.UserProfile;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.repository.ShopProfileRepository;
import com.ryan.logisticexpress.user.repository.StaffProfileRepository;
import com.ryan.logisticexpress.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ShopProfileRepository shopProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final AddressService addressService;
    private final HubOperationService hubOperationService;

    @Autowired
    public UserService(
            UserRepository userRepository,
            ShopProfileRepository shopProfileRepository,
            @Autowired(required = false) StaffProfileRepository staffProfileRepository,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) CustomUserDetailsService userDetailsService,
            @Autowired(required = false) AddressService addressService,
            @Autowired(required = false) HubOperationService hubOperationService
    ) {
        this.userRepository = userRepository;
        this.shopProfileRepository = shopProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.addressService = addressService;
        this.hubOperationService = hubOperationService;
    }

    public UserService(
            UserRepository userRepository,
            ShopProfileRepository shopProfileRepository,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService userDetailsService
    ) {
        this(userRepository, shopProfileRepository, null, passwordEncoder, userDetailsService, null, null);
    }

    public UserService(
            UserRepository userRepository,
            ShopProfileRepository shopProfileRepository,
            PasswordEncoder passwordEncoder
    ) {
        this(userRepository, shopProfileRepository, null, passwordEncoder, null, null, null);
    }

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
        AddressResponse pickupAddr = (addressService != null && shopProfile.getPickupAddressId() != null)
                ? addressService.getAddressResponse(shopProfile.getPickupAddressId())
                : null;
        return ShopProfileResponse.from(shopProfile, pickupAddr);
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

        // A4 Snapshot rule: when pickupAddress payload is provided, INSERT a new Address snapshot
        AddressResponse resolvedAddress = null;
        if (request.pickupAddress() != null) {
            if (addressService == null) {
                throw new BadRequestException("ADDRESS_SERVICE_UNAVAILABLE", "Address service is required to create pickup address");
            }
            Address created = addressService.createVerifiedAddressSnapshot(request.pickupAddress(), currentUserId);
            shopProfile.setPickupAddressId(created.getId());
            resolvedAddress = AddressResponse.from(created);
        } else if (request.pickupAddressId() != null) {
            if (addressService != null) {
                Address existing = addressService.getAddressOrThrow(request.pickupAddressId());
                resolvedAddress = AddressResponse.from(existing);
            }
            shopProfile.setPickupAddressId(request.pickupAddressId());
        } else {
            shopProfile.setPickupAddressId(null);
        }

        shopProfile.setShopName(shopName);
        shopProfile.setBankName(bankName);
        shopProfile.setBankAccountNo(bankAccountNo);
        shopProfile.setBankAccountHolder(bankAccountHolder);

        ShopProfile saved = shopProfileRepository.save(shopProfile);
        return ShopProfileResponse.from(saved, resolvedAddress);
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Role role, UUID hubId) {
        List<User> staff;
        if (hubId != null) {
            staff = userRepository.findStaffByHubAndRole(hubId, role);
        } else if (role != null) {
            staff = userRepository.findByRole(role);
        } else {
            staff = userRepository.findAllStaff();
        }
        return staff.stream().map(StaffResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Role role) {
        return listStaff(role, null);
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

        if (request.staffProfile() != null) {
            validateStaffProfileUniqueAndHub(null, request.staffProfile());
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

        if (request.staffProfile() != null) {
            StaffProfile sp = buildOrUpdateStaffProfile(user, null, request.staffProfile());
            user.setStaffProfile(sp);
        }

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

        if (request.staffProfile() != null) {
            validateStaffProfileUniqueAndHub(id, request.staffProfile());
        }

        UserProfile profile = user.getProfile();
        if (profile == null) {
            profile = UserProfile.builder().user(user).build();
            user.setProfile(profile);
        }
        profile.setFullName(request.fullName().trim());
        profile.setPhone(phone);

        if (request.roles() != null && !request.roles().isEmpty()) {
            if (!request.roles().equals(user.getRoles())) {
                user.incrementTokenVersion();
            }
            user.setRoles(request.roles());
        }

        if (request.status() != null) {
            user.setStatus(request.status());
            if (request.status() == Status.DISABLED) {
                user.incrementTokenVersion();
            }
        }

        if (request.staffProfile() != null) {
            StaffProfile sp = buildOrUpdateStaffProfile(user, user.getStaffProfile(), request.staffProfile());
            user.setStaffProfile(sp);
        }

        User saved = userRepository.save(user);
        if (userDetailsService != null) {
            userDetailsService.evictUserAuthState(saved.getId());
        }
        return StaffResponse.from(saved);
    }

    @Transactional
    public StaffResponse upsertStaffProfile(UUID userId, StaffProfileRequest request) {
        User user = findUserById(userId);
        boolean hasStaffRole = user.getRoles().stream().anyMatch(r -> r != Role.ROLE_SHOP);
        if (!hasStaffRole) {
            throw new BadRequestException("INVALID_ROLE", "Cannot assign staff profile to a non-staff user");
        }
        validateStaffProfileUniqueAndHub(userId, request);
        StaffProfile existing = user.getStaffProfile();
        if (existing == null && staffProfileRepository != null) {
            existing = staffProfileRepository.findById(userId).orElse(null);
        }
        StaffProfile sp = buildOrUpdateStaffProfile(user, existing, request);
        user.setStaffProfile(sp);
        if (staffProfileRepository != null) {
            staffProfileRepository.save(sp);
        } else {
            userRepository.save(user);
        }
        return StaffResponse.from(user);
    }

    @Transactional
    public void deleteStaff(UUID id) {
        User user = findUserById(id);
        user.setStatus(Status.DISABLED);
        user.incrementTokenVersion();
        userRepository.save(user);
        if (userDetailsService != null) {
            userDetailsService.evictUserAuthState(user.getId());
        }
    }

    private void validateStaffProfileUniqueAndHub(UUID currentUserId, StaffProfileRequest req) {
        if (hubOperationService != null) {
            hubOperationService.getHubOrThrow(req.hubId());
        }
        String empCode = req.employeeCode().trim().toUpperCase();
        if (staffProfileRepository != null) {
            staffProfileRepository.findByEmployeeCode(empCode)
                    .filter(existing -> !existing.getUserId().equals(currentUserId))
                    .ifPresent(existing -> {
                        throw new ConflictException("EMPLOYEE_CODE_EXISTS", "Employee code already exists: " + empCode);
                    });
        }
    }

    private StaffProfile buildOrUpdateStaffProfile(User user, StaffProfile target, StaffProfileRequest req) {
        String empCode = req.employeeCode().trim().toUpperCase();
        String plate = normalize(req.vehiclePlate());
        int maxCap = req.maxCapacityGram() != null ? req.maxCapacityGram() : 50000;
        int maxOrders = req.maxOrdersPerShift() != null ? req.maxOrdersPerShift() : 35;

        if (target == null) {
            return StaffProfile.builder()
                    .userId(user.getId())
                    .user(user)
                    .hubId(req.hubId())
                    .employeeCode(empCode)
                    .vehiclePlate(plate)
                    .maxCapacityGram(maxCap)
                    .maxOrdersPerShift(maxOrders)
                    .build();
        }
        target.setHubId(req.hubId());
        target.setEmployeeCode(empCode);
        target.setVehiclePlate(plate);
        target.setMaxCapacityGram(maxCap);
        target.setMaxOrdersPerShift(maxOrders);
        return target;
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(UUID id) {
        return findUserById(id);
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
