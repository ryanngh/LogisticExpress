package com.ryan.logisticexpress.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank(message = "Full name cannot be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Phone number cannot be blank")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Invalid phone number format")
        String phone,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl
) {}
