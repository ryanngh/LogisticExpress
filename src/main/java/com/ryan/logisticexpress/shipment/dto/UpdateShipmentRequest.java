package com.ryan.logisticexpress.shipment.dto;

import com.ryan.logisticexpress.address.dto.CreateVerifiedAddressRequest;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateShipmentRequest(
        @NotBlank(message = "Receiver name is required")
        String receiverName,

        @NotBlank(message = "Receiver phone is required")
        String receiverPhone,

        UUID deliveryAddressId,
        @Valid CreateVerifiedAddressRequest deliveryAddress,

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

        @NotNull(message = "Fee payer is required")
        FeePayer feePayer
) {}
