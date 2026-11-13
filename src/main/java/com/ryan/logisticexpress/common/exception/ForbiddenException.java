package com.ryan.logisticexpress.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseException {
    public ForbiddenException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }

    public ForbiddenException(String message) {
        this("FORBIDDEN", message);
    }
}
