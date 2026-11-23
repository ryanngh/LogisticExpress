package com.ryan.logisticexpress.shipment.exceptions;

import com.ryan.logisticexpress.common.exception.BaseException;
import com.ryan.logisticexpress.shipment.enums.ShipmentStatus;
import org.springframework.http.HttpStatus;

public class InvalidShipmentStateTransitionException extends BaseException {

    public InvalidShipmentStateTransitionException(ShipmentStatus currentStatus, ShipmentStatus targetStatus) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                String.format("Cannot transition shipment from %s to %s", currentStatus, targetStatus)
        );
    }

    public InvalidShipmentStateTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", message);
    }
}
