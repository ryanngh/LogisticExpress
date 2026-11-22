package com.ryan.logisticexpress.shipment.exceptions;

import com.ryan.logisticexpress.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ShipmentNotFoundException extends BaseException {

    public ShipmentNotFoundException(String identifier) {
        super(
                HttpStatus.NOT_FOUND,
                "SHIPMENT_NOT_FOUND",
                "Shipment not found: " + identifier
        );
    }
}
