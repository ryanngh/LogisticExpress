package com.ryan.logisticexpress.shipment.enums;

public enum ShipmentStatus {
    CREATED,
    CANCELLED,
    PICKUP_ASSIGNED,
    PICKED_UP,
    AT_ORIGIN_HUB,
    IN_LINEHAUL,
    AT_DEST_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    REFUSED,
    ON_HOLD_FOR_RETURN,
    RETURN_INITIATED,
    IN_RETURN_TRANSIT,
    RETURNED
}
