package com.ryan.logisticexpress.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StaffProfileRequest(
        @NotNull(message = "Hub ID is required")
        UUID hubId,

        @NotBlank(message = "Employee code cannot be blank")
        @Size(max = 20, message = "Employee code must not exceed 20 characters")
        String employeeCode,

        @Size(max = 20, message = "Vehicle plate must not exceed 20 characters")
        String vehiclePlate,

        @Min(value = 1, message = "Max capacity gram must be positive")
        Integer maxCapacityGram,

        @Min(value = 1, message = "Max orders per shift must be positive")
        Integer maxOrdersPerShift
) {}
