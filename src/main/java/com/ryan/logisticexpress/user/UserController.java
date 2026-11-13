package com.ryan.logisticexpress.user;

import com.ryan.logisticexpress.user.dto.*;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Xem thông tin cá nhân của người dùng hiện tại.
     * GET /api/users/me
     */
    @GetMapping("/api/users/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal UUID currentUserId
    ) {
        return ResponseEntity.ok(userService.getMyProfile(currentUserId));
    }

    /**
     * Cập nhật thông tin cá nhân của người dùng hiện tại.
     * PUT /api/users/me
     */
    @PutMapping("/api/users/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            @AuthenticationPrincipal UUID currentUserId
    ) {
        return ResponseEntity.ok(userService.updateMyProfile(currentUserId, request));
    }

    /**
     * Xem thông tin hồ sơ Shop của người dùng hiện tại.
     * GET /api/shop/profile
     */
    @GetMapping("/api/shop/profile")
    public ResponseEntity<ShopProfileResponse> getShopProfile(
            @AuthenticationPrincipal UUID currentUserId
    ) {
        return ResponseEntity.ok(userService.getShopProfile(currentUserId));
    }

    /**
     * Cập nhật thông tin hồ sơ Shop.
     * PUT /api/shop/profile
     */
    @PutMapping("/api/shop/profile")
    public ResponseEntity<ShopProfileResponse> updateShopProfile(
            @Valid @RequestBody UpdateShopProfileRequest request,
            @AuthenticationPrincipal UUID currentUserId
    ) {
        return ResponseEntity.ok(userService.updateShopProfile(currentUserId, request));
    }

    /**
     * Danh sách nhân viên nội bộ (Admin).
     * GET /api/ops/users
     */
    @GetMapping("/api/ops/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StaffResponse>> listStaff(
            @RequestParam(required = false) Role role
    ) {
        return ResponseEntity.ok(userService.listStaff(role));
    }

    /**
     * Xem chi tiết nhân viên nội bộ theo ID.
     * GET /api/ops/users/{id}
     */
    @GetMapping("/api/ops/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffResponse> getStaffById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.getStaffById(id));
    }

    /**
     * Tạo mới tài khoản nhân viên nội bộ.
     * POST /api/ops/users
     */
    @PostMapping("/api/ops/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffResponse> createStaff(
            @Valid @RequestBody CreateStaffRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createStaff(request));
    }

    /**
     * Cập nhật thông tin nhân viên nội bộ.
     * PUT /api/ops/users/{id}
     */
    @PutMapping("/api/ops/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffResponse> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRequest request
    ) {
        return ResponseEntity.ok(userService.updateStaff(id, request));
    }

    /**
     * Vô hiệu hóa tài khoản nhân viên nội bộ.
     * DELETE /api/ops/users/{id}
     */
    @DeleteMapping("/api/ops/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStaff(
            @PathVariable UUID id
    ) {
        userService.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }
}
