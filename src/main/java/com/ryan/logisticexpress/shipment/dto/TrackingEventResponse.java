package com.ryan.logisticexpress.shipment.dto;

import com.ryan.logisticexpress.shipment.entity.TrackingEvent;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TrackingEventResponse(
        UUID id,
        ShipmentStatus status,
        UUID hubId,
        String hubName,
        UUID actorId,
        String reasonCode,
        String note,
        BigDecimal lat,
        BigDecimal lng,
        Instant occurredAt
) {
    public static TrackingEventResponse from(TrackingEvent event) {
        return new TrackingEventResponse(
                event.getId(),
                event.getStatus(),
                event.getHub() != null ? event.getHub().getId() : null,
                event.getHub() != null ? event.getHub().getName() : null,
                event.getActor() != null ? event.getActor().getId() : null,
                event.getReasonCode(),
                event.getNote(),
                event.getLat(),
                event.getLng(),
                event.getOccurredAt()
        );
    }
}
