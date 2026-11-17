package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.enums.FeePayer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceQuoteRequest(
        @NotBlank(message = "Service type code cannot be blank (e.g., STANDARD, EXPRESS).")
        String serviceCode,

        @NotBlank(message = "• Sender province code cannot be blank.")
        String senderProvinceCode,

        @NotBlank(message = "Recipient province code cannot be blank.")
        String receiverProvinceCode,

        @NotNull(message = "• Actual weight (grams) cannot be blank.")
        @Min(value = 1, message = "• Weight must be larger than 0.")
        Integer actualWeightGram,

        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,

        BigDecimal declaredValue,
        BigDecimal codAmount,
        FeePayer feePayer
) {}