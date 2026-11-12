package com.ryan.logisticexpress.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {
    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }

    public ConflictException(String message) {
        this("CONFLICT", message);
    }
}
