package com.ryan.logisticexpress.shipment.repository;

import com.ryan.logisticexpress.shipment.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {

    List<TrackingEvent> findByShipmentIdOrderByOccurredAtAsc(UUID shipmentId);

    List<TrackingEvent> findByShipment_TrackingCodeOrderByOccurredAtAsc(String trackingCode);
}
