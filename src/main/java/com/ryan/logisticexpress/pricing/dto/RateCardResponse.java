package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.entity.RateCard;
import com.ryan.logisticexpress.pricing.enums.ZoneType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RateCardResponse(
        UUID id,
        UUID serviceTypeId,
        String serviceTypeCode,
        String serviceTypeName,
        ZoneType zoneType,
        Integer baseWeightGram,
        BigDecimal basePrice,
        Integer stepWeightGram,
        BigDecimal stepPrice,
        BigDecimal returnFeeRatio,
        Boolean isActive,
        Instant effectiveFrom
) {
    public static RateCardResponse from(RateCard rc) {
        return new RateCardResponse(
                rc.getId(),
                rc.getServiceType().getId(),
                rc.getServiceType().getCode(),
                rc.getServiceType().getName(),
                rc.getZoneType(),
                rc.getBaseWeightGram(),
                rc.getBasePrice(),
                rc.getStepWeightGram(),
                rc.getStepPrice(),
                rc.getReturnFeeRatio(),
                rc.getIsActive(),
                rc.getEffectiveFrom()
        );
    }
}
