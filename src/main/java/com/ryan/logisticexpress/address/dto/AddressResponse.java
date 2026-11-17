package com.ryan.logisticexpress.address.dto;

import com.ryan.logisticexpress.address.entity.Address;
import com.ryan.logisticexpress.address.enums.NormalizationStatus;
import java.math.BigDecimal;
import java.util.UUID;
public record AddressResponse(
        UUID id,
        String rawText,
        String streetDetail,
        String provinceCode,
        String provinceName,
        String wardCode,
        String wardName,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal matchScore,
        NormalizationStatus normalizationStatus
) {
    public static AddressResponse from(Address address) {
        String provinceCode = address.getProvince() != null ? address.getProvince().getCode() : null;
        String provinceName = address.getProvince() != null ? address.getProvince().getName() : null;
        String wardCode = address.getWard() != null ? address.getWard().getCode() : null;
        String wardName = address.getWard() != null ? address.getWard().getName() : null;
        return new AddressResponse(
                address.getId(),
                address.getRawText(),
                address.getStreetDetail(),
                provinceCode,
                provinceName,
                wardCode,
                wardName,
                address.getLat(),
                address.getLng(),
                address.getMatchScore(),
                address.getNormalizationStatus()
        );
    }
}
