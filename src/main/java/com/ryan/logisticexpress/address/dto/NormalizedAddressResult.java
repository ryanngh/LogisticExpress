package com.ryan.logisticexpress.address.dto;

import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record NormalizedAddressResult(
        String rawAddress,
        String cleanedAddress,
        String streetDetail,
        String newProvinceCode,
        String newProvinceName,
        String newWardCode,
        String newWardName,
        UUID assignedHubId,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal matchScore,
        NormalizationStatus status
) {}