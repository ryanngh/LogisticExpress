package com.ryan.logisticexpress.shipment.repository;

import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByTrackingCode(String trackingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.trackingCode = :trackingCode")
    Optional<Shipment> findByTrackingCodeForUpdate(@Param("trackingCode") String trackingCode);

    List<Shipment> findByCurrentHubIdAndCurrentStatusInAndDestHubIdNot(
            UUID currentHubId,
            Collection<ShipmentStatus> statuses,
            UUID excludeDestHubId
    );

    boolean existsByTrackingCode(String trackingCode);

    Optional<Shipment> findByShopIdAndIdempotencyKey(UUID shopId, String idempotencyKey);

    @Override
    @EntityGraph(attributePaths = {
            "serviceType",
            "originHub",
            "destHub",
            "currentHub",
            "assignedPickupShipper",
            "pickupAddress",
            "pickupAddress.province",
            "pickupAddress.ward",
            "deliveryAddress",
            "deliveryAddress.province",
            "deliveryAddress.ward"
    })
    Page<Shipment> findAll(Specification<Shipment> spec, Pageable pageable);
}
