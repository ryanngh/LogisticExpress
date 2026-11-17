package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.enums.ZoneType;

import java.math.BigDecimal;

public record PriceQuoteResponse(
        String serviceCode,
        String serviceName,
        ZoneType zoneType,
        Integer actualWeightGram,
        Integer volumetricWeightGram,
        Integer chargeableWeightGram,
        BigDecimal baseFee,
        BigDecimal insuranceFee,
        BigDecimal totalShippingFee,
        BigDecimal estimatedReturnFee,
        BigDecimal amountToCollect
) {}