package com.ryan.logisticexpress.dispatch;

import com.ryan.logisticexpress.dispatch.dto.DispatchDtos.*;
import com.ryan.logisticexpress.dispatch.service.DispatchService;
import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.dto.ShopActionDtos.*;
import com.ryan.logisticexpress.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ops/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;
    private final ShipmentService shipmentService;

    @PostMapping("/routes")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public ResponseEntity<DispatchRouteResponse> assignManualRoute(
            @Valid @RequestBody AssignRouteRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dispatchService.assignManualRoute(request, user.getId()));
    }

    @GetMapping("/routes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'HUB_STAFF')")
    public ResponseEntity<DispatchRouteResponse> getRoute(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.getRouteForOps(id, user.getId()));
    }

    @PostMapping("/shift-close")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'HUB_STAFF')")
    public ResponseEntity<ShiftCloseResponse> closeShift(
            @Valid @RequestBody CloseShiftRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.closeShift(request, user.getId()));
    }

    @PostMapping("/shipments/{id}/return/initiate")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'HUB_STAFF', 'CSKH')")
    public ResponseEntity<ShipmentResponse> initiateReturn(
            @PathVariable UUID id,
            @RequestBody(required = false) ReturnInitiateRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(shipmentService.initiateReturn(id, reason, user.getId()));
    }

    @PostMapping("/shipments/{id}/return/transit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'HUB_STAFF')")
    public ResponseEntity<ShipmentResponse> advanceReturnTransit(
            @PathVariable UUID id,
            @RequestBody(required = false) ReturnTransitRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(shipmentService.advanceReturnTransit(id, request, user.getId()));
    }

    @PostMapping("/shipments/{id}/return/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF')")
    public ResponseEntity<ShipmentResponse> completeReturnAtHub(
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteReturnRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ShipmentResponse.from(
                shipmentService.completeReturnEntity(id, request, user.getId())
        ));
    }
}
