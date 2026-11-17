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
        NormalizationStatus normalizationStatus
) {
    public static AddressResponse from(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressResponse(
                address.getId(),
                address.getRawText(),
                address.getStreetDetail(),
                address.getProvince() != null ? address.getProvince().getCode() : null,
                address.getProvince() != null ? address.getProvince().getName() : null,
                address.getWard() != null ? address.getWard().getCode() : null,
                address.getWard() != null ? address.getWard().getName() : null,
                address.getLat(),
                address.getLng(),
                address.getNormalizationStatus()
        );
    }
}
