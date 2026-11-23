package com.ryan.logisticexpress.shipment.service;

import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.entity.TrackingEvent;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.shipment.exceptions.InvalidShipmentStateTransitionException;
import com.ryan.logisticexpress.shipment.repository.ShipmentRepository;
import com.ryan.logisticexpress.shipment.repository.TrackingEventRepository;
import com.ryan.logisticexpress.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShipmentStateMachine {

    public static final int MAX_DELIVERY_ATTEMPTS = 3;
    public static final int MAX_PICKUP_ATTEMPTS = 3;
    public static final int MAX_RESCUE_COUNT = 1;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(ShipmentStatus.CREATED, Set.of(
                    ShipmentStatus.CANCELLED,
                    ShipmentStatus.PICKUP_ASSIGNED,
                    ShipmentStatus.AT_ORIGIN_HUB,
                    ShipmentStatus.AT_DEST_HUB
            )),
            Map.entry(ShipmentStatus.PICKUP_ASSIGNED, Set.of(
                    ShipmentStatus.PICKED_UP,
                    ShipmentStatus.PICKUP_ASSIGNED,
                    ShipmentStatus.CANCELLED
            )),
            Map.entry(ShipmentStatus.PICKED_UP, Set.of(
                    ShipmentStatus.AT_ORIGIN_HUB,
                    ShipmentStatus.AT_DEST_HUB
            )),
            Map.entry(ShipmentStatus.AT_ORIGIN_HUB, Set.of(
                    ShipmentStatus.IN_LINEHAUL
            )),
            Map.entry(ShipmentStatus.IN_LINEHAUL, Set.of(
                    ShipmentStatus.IN_LINEHAUL,
                    ShipmentStatus.AT_DEST_HUB
            )),
            Map.entry(ShipmentStatus.AT_DEST_HUB, Set.of(
                    ShipmentStatus.OUT_FOR_DELIVERY,
                    ShipmentStatus.RETURN_INITIATED
            )),
            Map.entry(ShipmentStatus.OUT_FOR_DELIVERY, Set.of(
                    ShipmentStatus.DELIVERED,
                    ShipmentStatus.DELIVERY_FAILED,
                    ShipmentStatus.REFUSED,
                    ShipmentStatus.AT_DEST_HUB
            )),
            Map.entry(ShipmentStatus.DELIVERY_FAILED, Set.of(
                    ShipmentStatus.AT_DEST_HUB,
                    ShipmentStatus.ON_HOLD_FOR_RETURN,
                    ShipmentStatus.RETURN_INITIATED
            )),
            Map.entry(ShipmentStatus.REFUSED, Set.of(
                    ShipmentStatus.ON_HOLD_FOR_RETURN,
                    ShipmentStatus.RETURN_INITIATED
            )),
            Map.entry(ShipmentStatus.ON_HOLD_FOR_RETURN, Set.of(
                    ShipmentStatus.AT_DEST_HUB,
                    ShipmentStatus.RETURN_INITIATED
            )),
            Map.entry(ShipmentStatus.RETURN_INITIATED, Set.of(
                    ShipmentStatus.IN_RETURN_TRANSIT
            )),
            Map.entry(ShipmentStatus.IN_RETURN_TRANSIT, Set.of(
                    ShipmentStatus.RETURNED
            )),
            Map.entry(ShipmentStatus.DELIVERED, Set.of()),
            Map.entry(ShipmentStatus.RETURNED, Set.of()),
            Map.entry(ShipmentStatus.CANCELLED, Set.of())
    );

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;

    @Transactional
    public Shipment validateAndTransition(
            Shipment shipment,
            ShipmentStatus targetStatus,
            User actor,
            String reason
    ) {
        return validateAndTransition(shipment, targetStatus, actor, null, reason, null, null, null);
    }

    @Transactional
    public Shipment validateAndTransition(
            Shipment shipment,
            ShipmentStatus targetStatus,
            User actor,
            String reasonCode,
            String note,
            Hub hub,
            BigDecimal lat,
            BigDecimal lng
    ) {
        ShipmentStatus current = shipment.getCurrentStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(targetStatus)) {
            throw new InvalidShipmentStateTransitionException(current, targetStatus);
        }

        if (targetStatus == ShipmentStatus.IN_LINEHAUL) {
            if ((shipment.getPickupAddress() != null && shipment.getPickupAddress().getNormalizationStatus() != NormalizationStatus.VERIFIED)
                    || (shipment.getDeliveryAddress() != null && shipment.getDeliveryAddress().getNormalizationStatus() != NormalizationStatus.VERIFIED)) {
                throw new InvalidShipmentStateTransitionException("Addresses must be VERIFIED before entering IN_LINEHAUL");
            }
        }

        if (current == ShipmentStatus.ON_HOLD_FOR_RETURN && targetStatus == ShipmentStatus.AT_DEST_HUB) {
            if (shipment.getRescueCount() >= MAX_RESCUE_COUNT) {
                throw new InvalidShipmentStateTransitionException("Shipment rescue limit already reached");
            }
            shipment.incrementRescueCount();
        }

        Hub eventHub = hub != null ? hub : shipment.getCurrentHub();
        Instant now = Instant.now();

        if (current == ShipmentStatus.PICKUP_ASSIGNED && targetStatus == ShipmentStatus.PICKUP_ASSIGNED) {
            shipment.incrementPickupAttemptCount();
            ShipmentStatus next = shipment.getPickupAttemptCount() >= MAX_PICKUP_ATTEMPTS
                    ? ShipmentStatus.CANCELLED
                    : ShipmentStatus.PICKUP_ASSIGNED;
            shipment.transitionToStatus(next);
            recordEvent(shipment, next, actor, eventHub, reasonCode, note, lat, lng, now);
            return shipmentRepository.save(shipment);
        }

        if (targetStatus == ShipmentStatus.DELIVERY_FAILED) {
            shipment.incrementAttemptCount();
            shipment.transitionToStatus(ShipmentStatus.DELIVERY_FAILED);
            recordEvent(shipment, ShipmentStatus.DELIVERY_FAILED, actor, eventHub, reasonCode, note, lat, lng, now);

            if (shipment.getRescueCount() >= MAX_RESCUE_COUNT) {
                shipment.transitionToStatus(ShipmentStatus.RETURN_INITIATED);
                recordEvent(shipment, ShipmentStatus.RETURN_INITIATED, actor, eventHub, reasonCode,
                        "Giao lại sau cứu đơn thất bại — tự động chuyển hoàn", lat, lng, now);
            } else if (shipment.getAttemptCount() >= MAX_DELIVERY_ATTEMPTS) {
                shipment.transitionToStatus(ShipmentStatus.ON_HOLD_FOR_RETURN);
                recordEvent(shipment, ShipmentStatus.ON_HOLD_FOR_RETURN, actor, eventHub, reasonCode,
                        "Giao thất bại 3 lần — giữ chờ xác nhận hoàn/cứu đơn", lat, lng, now);
            }
            return shipmentRepository.save(shipment);
        }

        if (targetStatus == ShipmentStatus.REFUSED) {
            shipment.transitionToStatus(ShipmentStatus.REFUSED);
            recordEvent(shipment, ShipmentStatus.REFUSED, actor, eventHub, reasonCode, note, lat, lng, now);

            ShipmentStatus followUp = shipment.getRescueCount() >= MAX_RESCUE_COUNT
                    ? ShipmentStatus.RETURN_INITIATED
                    : ShipmentStatus.ON_HOLD_FOR_RETURN;
            shipment.transitionToStatus(followUp);
            recordEvent(shipment, followUp, actor, eventHub, reasonCode,
                    followUp == ShipmentStatus.RETURN_INITIATED
                            ? "Khách từ chối nhận sau cứu đơn — tự động chuyển hoàn"
                            : "Khách từ chối nhận — giữ chờ xác nhận hoàn/cứu đơn",
                    lat, lng, now);
            return shipmentRepository.save(shipment);
        }

        shipment.transitionToStatus(targetStatus);
        recordEvent(shipment, targetStatus, actor, eventHub, reasonCode, note, lat, lng, now);
        return shipmentRepository.save(shipment);
    }

    private void recordEvent(
            Shipment shipment,
            ShipmentStatus status,
            User actor,
            Hub hub,
            String reasonCode,
            String note,
            BigDecimal lat,
            BigDecimal lng,
            Instant occurredAt
    ) {
        TrackingEvent event = TrackingEvent.builder()
                .shipment(shipment)
                .status(status)
                .actor(actor)
                .hub(hub)
                .reasonCode(reasonCode)
                .note(note)
                .lat(lat)
                .lng(lng)
                .occurredAt(occurredAt)
                .build();
        trackingEventRepository.save(event);
    }
}
