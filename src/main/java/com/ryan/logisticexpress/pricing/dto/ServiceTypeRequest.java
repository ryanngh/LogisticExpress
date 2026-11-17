package com.ryan.logisticexpress.pricing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceTypeRequest(
        @NotBlank(message = "Service type code cannot be blank")
        @Pattern(regexp = "^(STANDARD|EXPRESS)$", message = "Service type code must be STANDARD or EXPRESS")
        String code,

        @NotBlank(message = "Service type name cannot be blank")
        @Size(max = 100, message = "Service type name must not exceed 100 characters")
        String name,

        @NotNull(message = "Volumetric divisor is required")
        @Min(value = 1, message = "Volumetric divisor must be positive")
        Integer volumetricDivisor,

        @NotNull(message = "SLA hours is required")
        @Min(value = 1, message = "SLA hours must be positive")
        Integer slaHours
) {}
