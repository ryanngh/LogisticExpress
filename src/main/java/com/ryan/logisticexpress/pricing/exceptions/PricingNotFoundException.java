package com.ryan.logisticexpress.pricing.exceptions;

import com.ryan.logisticexpress.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PricingNotFoundException extends BaseException {
    public PricingNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "PRICING_NOT_FOUND", message);
    }
}