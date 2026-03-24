package com.electrahub.ocpp.exception;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {

    /**
     * Executes api error for `ApiError`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param status input consumed by ApiError.
     * @param error input consumed by ApiError.
     * @param message input consumed by ApiError.
     * @param path input consumed by ApiError.
     */
    public ApiError(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path);
    }

}
