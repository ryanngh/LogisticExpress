package com.ryan.logisticexpress.hub;

import com.ryan.logisticexpress.hub.dto.AssignWardsRequest;
import com.ryan.logisticexpress.hub.dto.HubRequest;
import com.ryan.logisticexpress.hub.dto.HubResponse;
import com.ryan.logisticexpress.hub.enums.HubLevel;
import com.ryan.logisticexpress.hub.service.HubOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ops/hubs")
@RequiredArgsConstructor
public class HubController {

    private final HubOperationService hubOperationService;

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
}
