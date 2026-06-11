package com.backend.ncba.BE_Demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SoapServiceUnavailableException extends ResponseStatusException {
    public SoapServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
