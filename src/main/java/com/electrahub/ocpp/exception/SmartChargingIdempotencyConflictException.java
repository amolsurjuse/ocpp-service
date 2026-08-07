package com.electrahub.ocpp.exception;

public class SmartChargingIdempotencyConflictException extends RuntimeException {
    public SmartChargingIdempotencyConflictException(String message) {
        super(message);
    }
}
