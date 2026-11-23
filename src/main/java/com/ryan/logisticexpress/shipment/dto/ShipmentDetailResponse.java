package com.ryan.logisticexpress.shipment.dto;

import java.util.List;

public record ShipmentDetailResponse(
        ShipmentResponse shipment,
        List<TrackingEventResponse> events,
        String barcodeBase64,
        String qrCodeBase64
) {}
