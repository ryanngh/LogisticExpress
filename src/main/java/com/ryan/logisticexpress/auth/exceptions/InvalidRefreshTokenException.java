package com.ryan.logisticexpress.auth.exceptions;

import com.ryan.logisticexpress.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends BaseException {
    public InvalidRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", message);
    }
}
