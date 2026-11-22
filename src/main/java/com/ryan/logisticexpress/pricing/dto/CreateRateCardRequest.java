package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.enums.ZoneType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRateCardRequest(
        @NotNull(message = "Service type ID is required")
        UUID serviceTypeId,

        @NotNull(message = "Zone type is required")
        ZoneType zoneType,

        @NotNull(message = "Base weight is required")
        @Min(value = 1, message = "Base weight must be > 0")
        Integer baseWeightGram,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.0", message = "Base price must be >= 0")
        BigDecimal basePrice,

        @NotNull(message = "Step weight is required")
        @Min(value = 1, message = "Step weight must be > 0")
        Integer stepWeightGram,

        @NotNull(message = "Step price is required")
        @DecimalMin(value = "0.0", message = "Step price must be >= 0")
        BigDecimal stepPrice,

        BigDecimal returnFeeRatio
) {}
