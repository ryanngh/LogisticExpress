package com.ryan.logisticexpress.hub;

import com.ryan.logisticexpress.hub.dto.AssignWardsRequest;
import com.ryan.logisticexpress.hub.dto.HubRequest;
import com.ryan.logisticexpress.hub.dto.HubResponse;
import com.ryan.logisticexpress.hub.dto.LinehaulDtos.*;
import com.ryan.logisticexpress.hub.enums.HubLevel;
import com.ryan.logisticexpress.hub.service.HubOperationService;
import com.ryan.logisticexpress.hub.service.ManifestService;
import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.shipment.dto.ShipmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ops/hubs")
@RequiredArgsConstructor
public class HubController {

    private final HubOperationService hubOperationService;
    private final ManifestService manifestService;

    @GetMapping
    public ResponseEntity<List<HubResponse>> listHubs(
            @RequestParam(required = false) HubLevel level
    ) {
        return ResponseEntity.ok(hubOperationService.listHubs(level));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HubResponse> getHubById(@PathVariable UUID id) {
        return ResponseEntity.ok(hubOperationService.getHubById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HubResponse> createHub(@Valid @RequestBody HubRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hubOperationService.createHub(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HubResponse> updateHub(
            @PathVariable UUID id,
            @Valid @RequestBody HubRequest request
    ) {
        return ResponseEntity.ok(hubOperationService.updateHub(id, request));
    }

    @PutMapping("/{id}/wards")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HubResponse> assignWards(
            @PathVariable UUID id,
            @Valid @RequestBody AssignWardsRequest request
    ) {
        return ResponseEntity.ok(hubOperationService.assignWardsToHub(id, request.wardCodes()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHub(@PathVariable UUID id) {
        hubOperationService.deleteHub(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/transit-scan")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF')")
    public ResponseEntity<ShipmentResponse> scanTransitHub(
            @Valid @RequestBody TransitScanRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(manifestService.scanTransitHub(request, user.getId()));
    }

    @GetMapping("/sort")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF', 'DISPATCHER')")
    public ResponseEntity<List<SortGroupResponse>> sortShipmentsByDestHub(
            @RequestParam(required = false) UUID hubId,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(manifestService.sortShipmentsByDestHub(hubId, user.getId()));
    }

    @PostMapping("/manifests")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF')")
    public ResponseEntity<ManifestResponse> createManifest(
            @Valid @RequestBody CreateManifestRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manifestService.createManifest(request, user.getId()));
    }

    @PostMapping("/manifests/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF')")
    public ResponseEntity<ManifestResponse> addManifestItem(
            @PathVariable UUID id,
            @Valid @RequestBody AddManifestItemRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(manifestService.addItemToManifest(id, request, user.getId()));
    }

    @PostMapping("/manifests/{id}/seal")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF')")
    public ResponseEntity<ManifestResponse> sealManifest(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(manifestService.sealManifest(id, user.getId()));
    }

    @GetMapping("/manifests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF', 'DISPATCHER')")
    public ResponseEntity<ManifestResponse> getManifest(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(manifestService.getManifest(id, user.getId()));
    }

    @PostMapping("/trips")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF', 'DISPATCHER')")
    public ResponseEntity<TripResponse> dispatchTrip(
            @Valid @RequestBody DispatchTripRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(manifestService.dispatchTrip(request, user.getId()));
    }

    @PostMapping("/manifests/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_STAFF')")
    public ResponseEntity<ManifestResponse> receiveManifest(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveManifestRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(manifestService.receiveManifest(id, request, user.getId()));
    }
}
