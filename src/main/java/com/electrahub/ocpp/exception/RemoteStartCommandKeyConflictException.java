package com.electrahub.ocpp.exception;

public class RemoteStartCommandKeyConflictException extends RuntimeException {

    public RemoteStartCommandKeyConflictException(String message) {
        super(message);
    }
}
