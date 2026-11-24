package com.ryan.logisticexpress.hub.repository;

import com.ryan.logisticexpress.hub.entity.ManifestItem;
import com.ryan.logisticexpress.hub.enums.ManifestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManifestItemRepository extends JpaRepository<ManifestItem, UUID> {

    List<ManifestItem> findByManifestId(UUID manifestId);

    Optional<ManifestItem> findByManifestIdAndShipmentId(UUID manifestId, UUID shipmentId);

    boolean existsByShipmentIdAndManifestStatusIn(UUID shipmentId, Collection<ManifestStatus> statuses);
}
