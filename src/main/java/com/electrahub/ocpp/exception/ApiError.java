package com.electrahub.ocpp.exception;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public ApiError(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path);
    }

}
