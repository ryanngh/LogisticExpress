package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.user.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateStaffRequest(
        @NotBlank(message = "Username cannot be blank")
        @Pattern(regexp = "^[a-z0-9_.]{3,50}$", message = "Username must be 3-50 characters (lowercase letters, numbers, _, .)")
        String username,

        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @NotBlank(message = "Full name cannot be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Phone number cannot be blank")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Invalid phone number format")
        String phone,

        @NotEmpty(message = "At least one role must be assigned")
        Set<Role> roles
) {}
