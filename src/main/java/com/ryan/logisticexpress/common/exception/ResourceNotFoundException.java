package com.ryan.logisticexpress.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }

    public ResourceNotFoundException(String message) {
        this("NOT_FOUND", message);
    }
}
