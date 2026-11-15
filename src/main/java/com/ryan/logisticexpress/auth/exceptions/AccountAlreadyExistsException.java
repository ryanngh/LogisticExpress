package com.ryan.logisticexpress.auth.exceptions;

import com.ryan.logisticexpress.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AccountAlreadyExistsException extends BaseException {
    public AccountAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", message);
    }
}
