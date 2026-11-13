package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateStaffRequest(
        @NotBlank(message = "Full name cannot be blank")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Phone number cannot be blank")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Invalid phone number format")
        String phone,

        @NotEmpty(message = "At least one role must be assigned")
        Set<Role> roles,

        Status status
) {}
