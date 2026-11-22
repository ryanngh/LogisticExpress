package com.ryan.logisticexpress.pricing.repository;

import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.enums.ZoneType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RateCardRepository extends JpaRepository<RateCard, UUID> {

    Optional<RateCard> findByServiceTypeIdAndZoneTypeAndIsActiveTrue(UUID serviceTypeId, ZoneType zoneType);

    List<RateCard> findByServiceTypeIdAndIsActiveTrue(UUID serviceTypeId);

    List<RateCard> findAllByIsActiveTrue();
}
