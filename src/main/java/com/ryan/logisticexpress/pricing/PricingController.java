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

    @PostMapping("/api/public/quote")
    public ResponseEntity<com.ryan.logisticexpress.pricing.dto.QuoteResponse> calculateQuote(
            @Valid @RequestBody com.ryan.logisticexpress.pricing.dto.QuoteRequest request
    ) {
        return ResponseEntity.ok(pricingEngineService.calculateQuote(request));
    }

    @GetMapping("/api/ops/rate-cards")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<com.ryan.logisticexpress.pricing.dto.RateCardResponse>> listRateCards() {
        return ResponseEntity.ok(pricingEngineService.listRateCards());
    }

    @PostMapping("/api/ops/rate-cards")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.ryan.logisticexpress.pricing.dto.RateCardResponse> createRateCard(
            @Valid @RequestBody com.ryan.logisticexpress.pricing.dto.CreateRateCardRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingEngineService.createRateCard(request));
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
