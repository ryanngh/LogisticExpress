package com.ryan.logisticexpress.dispatch;

import com.ryan.logisticexpress.dispatch.dto.DispatchDtos.*;
import com.ryan.logisticexpress.dispatch.service.DispatchService;
import com.ryan.logisticexpress.dispatch.service.DispatchService.PodImageContent;
import com.ryan.logisticexpress.privacy.service.PhoneMaskingService;
import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import com.ryan.logisticexpress.shipment.dto.ShopActionDtos.CompleteReturnRequest;
import com.ryan.logisticexpress.shipment.entity.Shipment;
import com.ryan.logisticexpress.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShipperTaskController {

    private static final Set<String> OPS_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_DISPATCHER", "ROLE_HUB_STAFF", "ROLE_MANAGER", "ROLE_CSKH", "ROLE_ACCOUNTANT"
    );

    private final DispatchService dispatchService;
    private final ShipmentService shipmentService;
    private final PhoneMaskingService phoneMaskingService;

    @GetMapping("/api/shipper/tasks")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<ShipperTasksResponse> getShipperTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.getShipperTasks(user.getId(), date));
    }

    @GetMapping("/api/shipper/stops/{stopId}")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<DeliveryStopResponse> getStop(
            @PathVariable UUID stopId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.getStopForShipper(stopId, user.getId()));
    }

    @PostMapping(value = "/api/shipper/stops/{stopId}/pod", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<PodUploadResponse> uploadPod(
            @PathVariable UUID stopId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.uploadPodImage(stopId, file, user.getId()));
    }

    @GetMapping("/api/pod/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getPodImage(
            @PathVariable String filename,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        boolean isOps = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(OPS_ROLES::contains);
        PodImageContent content = dispatchService.loadPodImage(filename, user.getId(), isOps);
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .body(content.bytes());
    }

    @PostMapping("/api/shipper/stops/{stopId}/deliver")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<DeliveryStopResponse> confirmDelivery(
            @PathVariable UUID stopId,
            @Valid @RequestBody(required = false) DeliverStopRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.confirmDelivery(stopId, request, user.getId()));
    }

    @PostMapping("/api/shipper/stops/{stopId}/fail")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<DeliveryStopResponse> reportFailure(
            @PathVariable UUID stopId,
            @Valid @RequestBody FailStopRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.reportDeliveryFailure(stopId, request, user.getId()));
    }

    @PostMapping("/api/shipper/stops/{stopId}/refuse")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<DeliveryStopResponse> reportRefusal(
            @PathVariable UUID stopId,
            @Valid @RequestBody(required = false) RefuseStopRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(dispatchService.reportDeliveryRefusal(stopId, request, user.getId()));
    }

    @PostMapping("/api/shipper/shipments/{shipmentId}/return-complete")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<ShipmentResponse> completeReturn(
            @PathVariable UUID shipmentId,
            @RequestBody(required = false) CompleteReturnRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        Shipment returned = shipmentService.completeReturnEntity(shipmentId, request, user.getId());
        return ResponseEntity.ok(phoneMaskingService.maskForShipper(returned, user.getId()));
    }
}
