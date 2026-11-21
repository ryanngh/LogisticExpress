package com.ryan.logisticexpress.user.dto;

import com.ryan.logisticexpress.user.entity.StaffProfile;

import java.util.UUID;

public record StaffProfileResponse(
        UUID userId,
        UUID hubId,
        String employeeCode,
        String vehiclePlate,
        Integer maxCapacityGram,
        Integer maxOrdersPerShift
) {
    public static StaffProfileResponse from(StaffProfile entity) {
        if (entity == null) {
            return null;
        }
        return new StaffProfileResponse(
                entity.getUserId(),
                entity.getHubId(),
                entity.getEmployeeCode(),
                entity.getVehiclePlate(),
                entity.getMaxCapacityGram(),
                entity.getMaxOrdersPerShift()
        );
    }
}
