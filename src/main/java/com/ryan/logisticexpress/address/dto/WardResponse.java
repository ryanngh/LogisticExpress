package com.ryan.logisticexpress.address.dto;

import com.ryan.logisticexpress.address.entity.Ward;

import java.math.BigDecimal;
import java.util.UUID;

public record WardResponse(
        String code,
        String provinceCode,
        String name,
        UUID assignedHubId,
        BigDecimal centerLat,
        BigDecimal centerLng
) {
    public static WardResponse from(Ward ward) {
        return new WardResponse(
                ward.getCode(),
                ward.getProvince() != null ? ward.getProvince().getCode() : null,
                ward.getName(),
                ward.getAssignedHubId(),
                ward.getCenterLat(),
                ward.getCenterLng()
        );
    }
}
