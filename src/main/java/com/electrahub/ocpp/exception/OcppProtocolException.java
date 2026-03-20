package com.electrahub.ocpp.exception;

public class OcppProtocolException extends RuntimeException {

    public OcppProtocolException(String message) {
        super(message);
    }

    public OcppProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

}
