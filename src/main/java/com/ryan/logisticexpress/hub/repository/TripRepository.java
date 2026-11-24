package com.ryan.logisticexpress.hub.repository;

import com.ryan.logisticexpress.hub.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    boolean existsByCode(String code);

    List<Trip> findByOriginHubIdOrderByDepartedAtDesc(UUID originHubId);

    List<Trip> findByDestHubIdOrderByDepartedAtDesc(UUID destHubId);
}
