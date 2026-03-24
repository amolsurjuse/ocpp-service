package com.electrahub.ocpp.exception;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
public class OcppProtocolException extends RuntimeException {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppProtocolException.class);


    /**
     * Executes ocpp protocol exception for `OcppProtocolException`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param message input consumed by OcppProtocolException.
     */
    public OcppProtocolException(String message) {
        super(message);
        LOGGER.info("CODEx_ENTRY_LOG: Entering OcppProtocolException#OcppProtocolException");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering OcppProtocolException#OcppProtocolException with debug context");
    }

    /**
     * Executes ocpp protocol exception for `OcppProtocolException`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param message input consumed by OcppProtocolException.
     * @param cause input consumed by OcppProtocolException.
     */
    public OcppProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

}
