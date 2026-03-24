package com.electrahub.ocpp.exception;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
public class OcppCommandTimeoutException extends RuntimeException {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppCommandTimeoutException.class);


    /**
     * Executes ocpp command timeout exception for `OcppCommandTimeoutException`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param message input consumed by OcppCommandTimeoutException.
     */
    public OcppCommandTimeoutException(String message) {
        super(message);
        LOGGER.info("CODEx_ENTRY_LOG: Entering OcppCommandTimeoutException#OcppCommandTimeoutException");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering OcppCommandTimeoutException#OcppCommandTimeoutException with debug context");
    }

    /**
     * Executes ocpp command timeout exception for `OcppCommandTimeoutException`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param message input consumed by OcppCommandTimeoutException.
     * @param cause input consumed by OcppCommandTimeoutException.
     */
    public OcppCommandTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

}
