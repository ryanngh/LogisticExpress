package com.ryan.logisticexpress.privacy.service;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PhoneMaskingService {

    public String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String clean = phone.trim();
        if (clean.length() <= 6) {
            return "*".repeat(clean.length());
        }
        return clean.substring(0, 3) + "****" + clean.substring(clean.length() - 3);
    }

    public String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String clean = name.trim();
        int lastSpace = clean.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace + 1 < clean.length()) {
            return clean.substring(0, lastSpace + 2) + "***";
        }
        return clean.substring(0, 1) + "***";
    }

    public AddressResponse maskAddress(AddressResponse address) {
        if (address == null) {
            return null;
        }
        return new AddressResponse(
                address.id(),
                null,
                null,
                address.provinceCode(),
                address.provinceName(),
                address.wardCode(),
                address.wardName(),
                null,
                null,
                address.normalizationStatus()
        );
    }

    public boolean canShipperViewSenderPii(Shipment shipment, UUID shipperUserId) {
        if (shipment == null || shipperUserId == null) {
            return false;
        }
        ShipmentStatus status = shipment.getCurrentStatus();
        if (status == ShipmentStatus.PICKUP_ASSIGNED) {
            return shipment.getAssignedPickupShipper() != null
                    && shipperUserId.equals(shipment.getAssignedPickupShipper().getId());
        }
        if (status == ShipmentStatus.IN_RETURN_TRANSIT) {
            return shipment.getAssignedDeliveryShipper() != null
                    && shipperUserId.equals(shipment.getAssignedDeliveryShipper().getId());
        }
        return false;
    }

    public boolean canShipperViewReceiverPii(Shipment shipment, UUID shipperUserId) {
        if (shipment == null || shipperUserId == null) {
            return false;
        }
        return shipment.getCurrentStatus() == ShipmentStatus.OUT_FOR_DELIVERY
                && shipment.getAssignedDeliveryShipper() != null
                && shipperUserId.equals(shipment.getAssignedDeliveryShipper().getId());
    }

    public ShipmentResponse maskForShipper(Shipment shipment, UUID shipperUserId) {
        ShipmentResponse raw = ShipmentResponse.from(shipment);
        boolean showSender = canShipperViewSenderPii(shipment, shipperUserId);
        boolean showReceiver = canShipperViewReceiverPii(shipment, shipperUserId);
        return new ShipmentResponse(
                raw.id(),
                raw.trackingCode(),
                raw.shopId(),
                raw.serviceTypeCode(),
                raw.serviceTypeName(),
                raw.currentStatus(),
                raw.originHubId(),
                raw.originHubName(),
                raw.destHubId(),
                raw.destHubName(),
                raw.currentHubId(),
                raw.currentHubName(),
                raw.assignedPickupShipperId(),
                showSender ? raw.senderName() : maskName(raw.senderName()),
                showSender ? raw.senderPhone() : maskPhone(raw.senderPhone()),
                showSender ? raw.pickupAddress() : maskAddress(raw.pickupAddress()),
                showReceiver ? raw.receiverName() : maskName(raw.receiverName()),
                showReceiver ? raw.receiverPhone() : maskPhone(raw.receiverPhone()),
                showReceiver ? raw.deliveryAddress() : maskAddress(raw.deliveryAddress()),
                raw.actualWeightGram(),
                raw.lengthCm(),
                raw.widthCm(),
                raw.heightCm(),
                raw.chargeableWeightGram(),
                raw.declaredValue(),
                raw.codAmount(),
                raw.baseFee(),
                raw.shippingFee(),
                raw.insuranceFee(),
                raw.returnFee(),
                raw.feePayer(),
                raw.amountToCollect(),
                raw.deliveryTimeWindowStart(),
                raw.deliveryTimeWindowEnd(),
                raw.scheduledDeliveryDate(),
                raw.pickupAttemptCount(),
                raw.attemptCount(),
                raw.rescueCount(),
                raw.createdAt(),
                raw.deliveredAt()
        );
    }
}
