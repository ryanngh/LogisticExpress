package com.ryan.logisticexpress.shipment.repository;

import com.ryan.logisticexpress.shipment.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {

    Optional<Shipment> findByTrackingCode(String trackingCode);

    boolean existsByTrackingCode(String trackingCode);

    Optional<Shipment> findByShopIdAndIdempotencyKey(UUID shopId, String idempotencyKey);

    @Override
    @EntityGraph(attributePaths = {
            "serviceType",
            "originHub",
            "destHub",
            "pickupAddress",
            "pickupAddress.province",
            "pickupAddress.ward",
            "deliveryAddress",
            "deliveryAddress.province",
            "deliveryAddress.ward"
    })
    Page<Shipment> findAll(Specification<Shipment> spec, Pageable pageable);
}
