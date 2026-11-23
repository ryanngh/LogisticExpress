package com.ryan.logisticexpress.shipment.dto;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        String trackingCode,
        UUID shopId,
        String serviceTypeCode,
        String serviceTypeName,
        ShipmentStatus currentStatus,
        UUID originHubId,
        String originHubName,
        UUID destHubId,
        String destHubName,
        String senderName,
        String senderPhone,
        AddressResponse pickupAddress,
        String receiverName,
        String receiverPhone,
        AddressResponse deliveryAddress,
        Integer actualWeightGram,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        Integer chargeableWeightGram,
        BigDecimal declaredValue,
        BigDecimal codAmount,
        BigDecimal baseFee,
        BigDecimal shippingFee,
        BigDecimal insuranceFee,
        BigDecimal returnFee,
        FeePayer feePayer,
        BigDecimal amountToCollect,
        Integer deliveryTimeWindowStart,
        Integer deliveryTimeWindowEnd,
        LocalDate scheduledDeliveryDate,
        Integer pickupAttemptCount,
        Integer attemptCount,
        Integer rescueCount,
        Instant createdAt,
        Instant deliveredAt
) {
    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(
                s.getId(),
                s.getTrackingCode(),
                s.getShop().getId(),
                s.getServiceType().getCode(),
                s.getServiceType().getName(),
                s.getCurrentStatus(),
                s.getOriginHub() != null ? s.getOriginHub().getId() : null,
                s.getOriginHub() != null ? s.getOriginHub().getName() : null,
                s.getDestHub() != null ? s.getDestHub().getId() : null,
                s.getDestHub() != null ? s.getDestHub().getName() : null,
                s.getSenderName(),
                s.getSenderPhone(),
                AddressResponse.from(s.getPickupAddress()),
                s.getReceiverName(),
                s.getReceiverPhone(),
                AddressResponse.from(s.getDeliveryAddress()),
                s.getActualWeightGram(),
                s.getLengthCm(),
                s.getWidthCm(),
                s.getHeightCm(),
                s.getChargeableWeightGram(),
                s.getDeclaredValue(),
                s.getCodAmount(),
                s.getBaseFee(),
                s.getShippingFee(),
                s.getInsuranceFee(),
                s.getReturnFee(),
                s.getFeePayer(),
                s.getAmountToCollect(),
                s.getDeliveryTimeWindowStart(),
                s.getDeliveryTimeWindowEnd(),
                s.getScheduledDeliveryDate(),
                s.getPickupAttemptCount(),
                s.getAttemptCount(),
                s.getRescueCount(),
                s.getCreatedAt(),
                s.getDeliveredAt()
        );
    }
}
