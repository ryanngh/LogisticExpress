package com.ryan.logisticexpress.dispatch.repository;

import com.ryan.logisticexpress.dispatch.entity.DeliveryStop;
import com.ryan.logisticexpress.dispatch.enums.StopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryStopRepository extends JpaRepository<DeliveryStop, UUID> {

    List<DeliveryStop> findByRouteIdOrderByStopSequenceAsc(UUID routeId);

    List<DeliveryStop> findByRouteIdInOrderByStopSequenceAsc(Collection<UUID> routeIds);

    Optional<DeliveryStop> findByShipmentIdAndStopStatusIn(UUID shipmentId, Collection<StopStatus> statuses);

    boolean existsByRouteIdAndStopStatusIn(UUID routeId, Collection<StopStatus> statuses);

    Optional<DeliveryStop> findFirstByPodImageUrl(String podImageUrl);
}
