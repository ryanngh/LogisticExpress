package com.ryan.logisticexpress.shipment.dto;

import com.ryan.logisticexpress.shipment.enums.FailureReasonCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public final class PickupIntakeDtos {

    private PickupIntakeDtos() {
    }

    public record AssignPickupRequest(
            @NotNull(message = "Shipper ID is required")
            UUID shipperId,
            String note
    ) {
    }

    public record PickupResultRequest(
            @NotNull(message = "Pickup result (success) is required")
            Boolean success,
            FailureReasonCode reasonCode,
            String note,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }

    public record IntakeShipmentRequest(
            @NotBlank(message = "Tracking code is required")
            String trackingCode,
            UUID hubId,
            String note
    ) {
    }

    public record ReweighShipmentRequest(
            @NotNull(message = "Actual weight is required")
            @Positive(message = "Actual weight must be positive")
            Integer actualWeightGram,
            @Positive(message = "Length must be positive")
            Integer lengthCm,
            @Positive(message = "Width must be positive")
            Integer widthCm,
            @Positive(message = "Height must be positive")
            Integer heightCm,
            String note
    ) {
    }

    public record ReweighShipmentResponse(
            ShipmentResponse shipment,
            Integer oldChargeableWeightGram,
            Integer newChargeableWeightGram,
            Integer weightDeltaGram,
            BigDecimal oldShippingFee,
            BigDecimal newShippingFee,
            BigDecimal shippingFeeDelta,
            BigDecimal oldAmountToCollect,
            BigDecimal newAmountToCollect
    ) {
    }
}
