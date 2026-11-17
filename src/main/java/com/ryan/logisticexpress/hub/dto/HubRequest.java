package com.ryan.logisticexpress.hub.dto;

import com.ryan.logisticexpress.hub.enums.HubLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HubRequest(
        @NotBlank(message = "Hub code cannot be blank")
        @Size(max = 30, message = "Hub code must not exceed 30 characters")
        String code,

        @NotBlank(message = "Hub name cannot be blank")
        @Size(max = 150, message = "Hub name must not exceed 150 characters")
        String name,

        @NotNull(message = "Hub level is required")
        HubLevel level,

        UUID parentHubId,
        UUID addressId,

        @NotNull(message = "Latitude is required")
        BigDecimal lat,

        @NotNull(message = "Longitude is required")
        BigDecimal lng,

        List<String> assignedWardCodes
) {}
