package com.ryan.logisticexpress.pricing.repository;

import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.enums.ZoneType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RateCardRepository extends JpaRepository<RateCard, UUID> {
    Optional<RateCard> findByServiceTypeIdAndZoneType(UUID serviceTypeId, ZoneType zoneType);
}