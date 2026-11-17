package com.ryan.logisticexpress.pricing.dto;

import com.ryan.logisticexpress.pricing.entity.ServiceType;

import java.util.UUID;

public record ServiceTypeResponse(
        UUID id,
        String code,
        String name,
        Integer volumetricDivisor,
        Integer slaHours
) {
    public static ServiceTypeResponse from(ServiceType entity) {
        return new ServiceTypeResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getVolumetricDivisor(),
                entity.getSlaHours()
        );
    }
}
