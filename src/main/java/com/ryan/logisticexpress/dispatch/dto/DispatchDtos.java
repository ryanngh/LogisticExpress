package com.ryan.logisticexpress.dispatch.dto;

import com.ryan.logisticexpress.address.dto.AddressResponse;
import com.ryan.logisticexpress.dispatch.entity.DeliveryStop;
import com.ryan.logisticexpress.dispatch.entity.DispatchRoute;
import com.ryan.logisticexpress.dispatch.enums.RouteStatus;
import com.ryan.logisticexpress.dispatch.enums.StopStatus;
import com.ryan.logisticexpress.pricing.enums.FeePayer;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.enums.FailureReasonCode;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DispatchDtos {

    private DispatchDtos() {
    }

    public record AssignRouteRequest(
            @NotNull(message = "Shipper ID is required")
            UUID shipperId,
            @NotEmpty(message = "At least one shipment ID is required")
            List<UUID> shipmentIds,
            UUID hubId,
            LocalDate routeDate,
            String note
    ) {
    }

    public record DeliverStopRequest(
            @Size(max = 500, message = "POD image URL is too long")
            String podImageUrl,
            @Size(max = 262144, message = "Receiver signature payload is too large")
            String podReceiverSignature,
            String note,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }

    public record FailStopRequest(
            @NotNull(message = "Failure reason code is required")
            FailureReasonCode reasonCode,
            String note,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }

    public record RefuseStopRequest(
            FailureReasonCode reasonCode,
            String note,
            BigDecimal lat,
            BigDecimal lng
    ) {
    }

    public record CloseShiftRequest(
            @NotNull(message = "Shipper ID is required")
            UUID shipperId,
            UUID hubId,
            LocalDate routeDate,
            List<String> returnedTrackingCodes,
            String note
    ) {
    }

    public record PodUploadResponse(
            UUID stopId,
            String podImageUrl
    ) {
    }

    public record DeliveryStopResponse(
            UUID id,
            UUID routeId,
            UUID shipmentId,
            String trackingCode,
            Integer stopSequence,
            StopStatus stopStatus,
            ShipmentStatus shipmentStatus,
            Integer attemptCount,
            Integer rescueCount,
            String senderName,
            String senderPhone,
            AddressResponse pickupAddress,
            String receiverName,
            String receiverPhone,
            AddressResponse deliveryAddress,
            BigDecimal codAmount,
            BigDecimal shippingFee,
            FeePayer feePayer,
            BigDecimal expectedCodAmount,
            Integer timeWindowStartMin,
            Integer timeWindowEndMin,
            Instant actualArrivalAt,
            String podImageUrl,
            String podReceiverSignature,
            String failureReasonCode
    ) {
        public static DeliveryStopResponse from(DeliveryStop stop, ShipmentResponse shipmentView) {
            return new DeliveryStopResponse(
                    stop.getId(),
                    stop.getRoute().getId(),
                    shipmentView.id(),
                    shipmentView.trackingCode(),
                    stop.getStopSequence(),
                    stop.getStopStatus(),
                    shipmentView.currentStatus(),
                    shipmentView.attemptCount(),
                    shipmentView.rescueCount(),
                    shipmentView.senderName(),
                    shipmentView.senderPhone(),
                    shipmentView.pickupAddress(),
                    shipmentView.receiverName(),
                    shipmentView.receiverPhone(),
                    shipmentView.deliveryAddress(),
                    shipmentView.codAmount(),
                    shipmentView.shippingFee(),
                    shipmentView.feePayer(),
                    shipmentView.amountToCollect(),
                    stop.getTimeWindowStartMin(),
                    stop.getTimeWindowEndMin(),
                    stop.getActualArrivalAt(),
                    stop.getPodImageUrl(),
                    stop.getPodReceiverSignature(),
                    stop.getFailureReasonCode()
            );
        }
    }

    public record DispatchRouteResponse(
            UUID id,
            UUID optimizationBatchId,
            UUID hubId,
            String hubCode,
            UUID shipperId,
            String shipperUsername,
            LocalDate routeDate,
            RouteStatus status,
            BigDecimal optimizedDistanceKm,
            Integer totalParcels,
            Integer totalWeightGram,
            List<DeliveryStopResponse> stops
    ) {
        public static DispatchRouteResponse from(DispatchRoute route, List<DeliveryStopResponse> stops) {
            return new DispatchRouteResponse(
                    route.getId(),
                    route.getOptimizationBatchId(),
                    route.getHub().getId(),
                    route.getHub().getCode(),
                    route.getShipper().getId(),
                    route.getShipper().getUsername(),
                    route.getRouteDate(),
                    route.getStatus(),
                    route.getOptimizedDistanceKm(),
                    route.getTotalParcels(),
                    route.getTotalWeightGram(),
                    stops
            );
        }
    }

    public record ShipperTasksResponse(
            LocalDate routeDate,
            List<DispatchRouteResponse> routes,
            List<ShipmentResponse> returnTasks
    ) {
    }

    public record ShiftCodItem(
            UUID shipmentId,
            String trackingCode,
            BigDecimal amountToCollect
    ) {
        public static ShiftCodItem from(Shipment shipment) {
            return new ShiftCodItem(
                    shipment.getId(),
                    shipment.getTrackingCode(),
                    shipment.getAmountToCollect()
            );
        }
    }

    public record ShiftCloseResponse(
            UUID shipperId,
            UUID hubId,
            LocalDate routeDate,
            List<UUID> closedRouteIds,
            int deliveredCount,
            List<String> returnedToHubTrackingCodes,
            List<String> onHoldReturnedTrackingCodes,
            BigDecimal expectedCodTotal,
            List<ShiftCodItem> deliveredCodItems
    ) {
    }
}
