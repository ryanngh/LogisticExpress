package com.ryan.logisticexpress.hub.dto;

import com.ryan.logisticexpress.hub.entity.Manifest;
import com.ryan.logisticexpress.hub.entity.ManifestItem;
import com.ryan.logisticexpress.hub.entity.Trip;
import com.ryan.logisticexpress.hub.enums.ManifestStatus;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LinehaulDtos {

    private LinehaulDtos() {
    }

    public record TransitScanRequest(
            @NotBlank(message = "Tracking code is required")
            String trackingCode,
            UUID hubId,
            String note
    ) {
    }

    public record SortGroupResponse(
            UUID destHubId,
            String destHubCode,
            String destHubName,
            int shipmentCount,
            int totalWeightGram,
            List<ShipmentResponse> shipments
    ) {
    }

    public record CreateManifestRequest(
            UUID originHubId,
            @NotNull(message = "Destination hub ID is required")
            UUID destHubId,
            List<String> trackingCodes
    ) {
    }

    public record AddManifestItemRequest(
            @NotBlank(message = "Tracking code is required")
            String trackingCode
    ) {
    }

    public record DispatchTripRequest(
            UUID originHubId,
            @NotNull(message = "Destination hub ID is required")
            UUID destHubId,
            @NotEmpty(message = "At least one sealed manifest is required")
            List<UUID> manifestIds,
            String vehiclePlate,
            String driverName
    ) {
    }

    public record ReceiveManifestRequest(
            @NotNull(message = "Scanned tracking codes list is required")
            List<String> scannedTrackingCodes,
            String note
    ) {
    }

    public record ManifestItemResponse(
            UUID id,
            UUID shipmentId,
            String trackingCode,
            Instant scanOutAt,
            Instant scanInAt,
            boolean isMissing,
            boolean isExtra
    ) {
        public static ManifestItemResponse from(ManifestItem item) {
            return new ManifestItemResponse(
                    item.getId(),
                    item.getShipment().getId(),
                    item.getShipment().getTrackingCode(),
                    item.getScanOutAt(),
                    item.getScanInAt(),
                    item.isMissing(),
                    item.isExtra()
            );
        }
    }

    public record ManifestResponse(
            UUID id,
            String code,
            UUID tripId,
            UUID originHubId,
            UUID destHubId,
            ManifestStatus status,
            UUID createdBy,
            UUID receivedBy,
            Instant sealedAt,
            Instant receivedAt,
            List<ManifestItemResponse> items
    ) {
        public static ManifestResponse from(Manifest manifest, List<ManifestItem> items) {
            return new ManifestResponse(
                    manifest.getId(),
                    manifest.getCode(),
                    manifest.getTrip() != null ? manifest.getTrip().getId() : null,
                    manifest.getOriginHub().getId(),
                    manifest.getDestHub().getId(),
                    manifest.getStatus(),
                    manifest.getCreatedBy().getId(),
                    manifest.getReceivedBy() != null ? manifest.getReceivedBy().getId() : null,
                    manifest.getSealedAt(),
                    manifest.getReceivedAt(),
                    items.stream().map(ManifestItemResponse::from).toList()
            );
        }
    }

    public record TripResponse(
            UUID id,
            String code,
            UUID originHubId,
            UUID destHubId,
            String vehiclePlate,
            String driverName,
            Trip.Status status,
            Instant departedAt,
            Instant arrivedAt,
            List<UUID> manifestIds
    ) {
        public static TripResponse from(Trip trip, List<UUID> manifestIds) {
            return new TripResponse(
                    trip.getId(),
                    trip.getCode(),
                    trip.getOriginHub().getId(),
                    trip.getDestHub().getId(),
                    trip.getVehiclePlate(),
                    trip.getDriverName(),
                    trip.getStatus(),
                    trip.getDepartedAt(),
                    trip.getArrivedAt(),
                    manifestIds
            );
        }
    }
}
