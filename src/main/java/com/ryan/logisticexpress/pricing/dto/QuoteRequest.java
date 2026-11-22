package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.enums.FeePayer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QuoteRequest(
        @NotBlank(message = "Service type code is required")
        String serviceTypeCode,

        @NotBlank(message = "Sender province code is required")
        String senderProvinceCode,

        @NotBlank(message = "Receiver province code is required")
        String receiverProvinceCode,

        @NotNull(message = "Actual weight is required")
        @Min(value = 1, message = "Weight must be at least 1 gram")
        Integer actualWeightGram,

        @Min(value = 1, message = "Length must be >= 1 cm")
        Integer lengthCm,

        @Min(value = 1, message = "Width must be >= 1 cm")
        Integer widthCm,

        @Min(value = 1, message = "Height must be >= 1 cm")
        Integer heightCm,

        @DecimalMin(value = "0.0", message = "Declared value must be >= 0")
        BigDecimal declaredValue,

        @DecimalMin(value = "0.0", message = "COD amount must be >= 0")
        BigDecimal codAmount,

        FeePayer feePayer
) {}
