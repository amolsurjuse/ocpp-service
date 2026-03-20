package com.electrahub.ocpp.exception;

public class OcppCommandTimeoutException extends RuntimeException {

    public OcppCommandTimeoutException(String message) {
        super(message);
    }

    public OcppCommandTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

}
