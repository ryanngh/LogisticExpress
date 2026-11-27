package com.ryan.logisticexpress.shipment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public final class ShopActionDtos {

    private ShopActionDtos() {
    }

    public enum ShopActionType {
        REDELIVER,
        RETURN,
        ADJUST_COD
    }

    public record ShopShipmentActionRequest(
            @NotNull(message = "Action type is required")
            ShopActionType actionType,
            @DecimalMin(value = "0.00", message = "COD amount cannot be negative")
            BigDecimal newCodAmount,
            String reason
    ) {
    }

    public record ReturnInitiateRequest(
            String reason
    ) {
    }

    public record ReturnTransitRequest(
            UUID hubId,
            UUID shipperId,
            String note
    ) {
    }

    public record CompleteReturnRequest(
            String note,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }
}
