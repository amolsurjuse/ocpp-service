package com.electrahub.ocpp.exception;

public class SmartChargingIdempotencyUnavailableException extends RuntimeException {
    public SmartChargingIdempotencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
