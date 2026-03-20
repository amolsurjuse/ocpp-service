package com.electrahub.ocpp.exception;

public class ChargePointNotConnectedException extends RuntimeException {

    public ChargePointNotConnectedException(String message) {
        super(message);
    }

    public ChargePointNotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }

}
