package com.ryan.logisticexpress.hub.dto;

import com.ryan.logisticexpress.hub.entity.Hub;
import com.ryan.logisticexpress.hub.enums.HubLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HubResponse(
        UUID id,
        String code,
        String name,
        HubLevel level,
        UUID parentHubId,
        UUID addressId,
        BigDecimal lat,
        BigDecimal lng,
        List<String> assignedWardCodes,
        Instant createdAt,
        Instant updatedAt
) {
    public static HubResponse from(Hub hub, List<String> assignedWardCodes) {
        return new HubResponse(
                hub.getId(),
                hub.getCode(),
                hub.getName(),
                hub.getLevel(),
                hub.getParentHub() != null ? hub.getParentHub().getId() : null,
                hub.getAddressId(),
                hub.getLat(),
                hub.getLng(),
                assignedWardCodes != null ? assignedWardCodes : List.of(),
                hub.getCreatedAt(),
                hub.getUpdatedAt()
        );
    }

    public static HubResponse from(Hub hub) {
        return from(hub, List.of());
    }
}
