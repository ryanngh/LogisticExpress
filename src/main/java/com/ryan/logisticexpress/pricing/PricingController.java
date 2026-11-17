package com.ryan.logisticexpress.pricing;

import com.ryan.logisticexpress.pricing.dto.PriceQuoteRequest;
import com.ryan.logisticexpress.pricing.dto.PriceQuoteResponse;
import com.ryan.logisticexpress.pricing.service.PricingEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingEngineService pricingEngineService;

    @PostMapping("/quote")
    public ResponseEntity<PriceQuoteResponse> getQuote(@Valid @RequestBody PriceQuoteRequest request) {
        PriceQuoteResponse response = pricingEngineService.calculateQuote(request);
        return ResponseEntity.ok(response);
    }
}