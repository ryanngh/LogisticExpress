package com.ryan.logisticexpress.pricing;

import com.ryan.logisticexpress.pricing.dto.ServiceTypeRequest;
import com.ryan.logisticexpress.pricing.dto.ServiceTypeResponse;
import com.ryan.logisticexpress.pricing.service.PricingEngineService;
import com.ryan.logisticexpress.shipment.enums.FailureReasonCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PricingController {

    private final PricingEngineService pricingEngineService;

    @GetMapping({"/api/public/service-types", "/api/ops/service-types"})
    public ResponseEntity<List<ServiceTypeResponse>> listServiceTypes() {
        return ResponseEntity.ok(pricingEngineService.listServiceTypes());
    }

    @PostMapping("/api/ops/service-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceTypeResponse> createServiceType(@Valid @RequestBody ServiceTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingEngineService.createServiceType(request));
    }

    @PutMapping("/api/ops/service-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceTypeResponse> updateServiceType(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceTypeRequest request
    ) {
        return ResponseEntity.ok(pricingEngineService.updateServiceType(id, request));
    }

    @GetMapping({"/api/public/failure-reasons", "/api/ops/failure-reasons"})
    public ResponseEntity<List<FailureReasonCode>> listFailureReasons() {
        return ResponseEntity.ok(Arrays.asList(FailureReasonCode.values()));
    }
}
