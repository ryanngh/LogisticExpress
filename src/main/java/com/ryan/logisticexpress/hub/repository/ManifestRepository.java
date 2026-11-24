package com.ryan.logisticexpress.hub.repository;

import com.ryan.logisticexpress.hub.entity.Manifest;
import com.ryan.logisticexpress.hub.enums.ManifestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ManifestRepository extends JpaRepository<Manifest, UUID> {

    boolean existsByCode(String code);

    List<Manifest> findByOriginHubIdAndStatusIn(UUID originHubId, Collection<ManifestStatus> statuses);

    List<Manifest> findByDestHubIdAndStatusIn(UUID destHubId, Collection<ManifestStatus> statuses);

    List<Manifest> findByTripId(UUID tripId);

    boolean existsByTripIdAndStatusNotIn(UUID tripId, Collection<ManifestStatus> statuses);
}
