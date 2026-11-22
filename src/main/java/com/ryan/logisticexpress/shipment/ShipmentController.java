package com.ryan.logisticexpress.shipment;

import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.shipment.dto.*;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import com.ryan.logisticexpress.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping("/api/shop/shipments")
    @PreAuthorize("hasRole('SHOP')")
    public ResponseEntity<ShipmentResponse> createShipment(
            @Valid @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        ShipmentResponse response = shipmentService.createShipment(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/shop/shipments")
    @PreAuthorize("hasRole('SHOP')")
    public ResponseEntity<Page<ShipmentResponse>> getMyShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        Page<ShipmentResponse> page = shipmentService.searchShipments(user.getId(), status, trackingCode, fromDate, toDate, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/api/shop/shipments/{id}")
    @PreAuthorize("hasRole('SHOP')")
    public ResponseEntity<ShipmentResponse> getShipmentById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(shipmentService.getShipmentById(id, user.getId(), false));
    }

    @GetMapping("/api/shop/shipments/code/{trackingCode}")
    @PreAuthorize("hasRole('SHOP')")
    public ResponseEntity<ShipmentDetailResponse> getShipmentDetail(
            @PathVariable String trackingCode,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(shipmentService.getShipmentDetail(trackingCode, user.getId(), false));
    }

    @PutMapping("/api/shop/shipments/{id}")
    @PreAuthorize("hasRole('SHOP')")
    public ResponseEntity<ShipmentResponse> updateShipment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShipmentRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(shipmentService.updateShipment(id, request, user.getId(), false));
    }

    @PostMapping("/api/shop/shipments/{id}/cancel")
    @PreAuthorize("hasRole('SHOP')")
    public ResponseEntity<ShipmentResponse> cancelShipment(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Hủy đơn hàng bởi người gửi";
        return ResponseEntity.ok(shipmentService.cancelShipment(id, reason, user.getId(), false));
    }

    @GetMapping("/api/ops/shipments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'HUB_STAFF', 'MANAGER', 'CSKH')")
    public ResponseEntity<Page<ShipmentResponse>> searchOpsShipments(
            @RequestParam(required = false) UUID shopId,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ShipmentResponse> page = shipmentService.searchShipments(shopId, status, trackingCode, fromDate, toDate, pageable);
        return ResponseEntity.ok(page);
    }
}
