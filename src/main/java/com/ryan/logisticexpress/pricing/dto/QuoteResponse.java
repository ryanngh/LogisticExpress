package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.pricing.enums.ZoneType;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteResponse(
        UUID rateCardId,
        String serviceTypeCode,
        String serviceTypeName,
        ZoneType zoneType,
        Integer actualWeightGram,
        Integer volumetricWeightGram,
        Integer chargeableWeightGram,
        BigDecimal baseFee,
        BigDecimal insuranceFee,
        BigDecimal shippingFee,
        BigDecimal returnFee,
        FeePayer feePayer,
        BigDecimal amountToCollect
) {}
