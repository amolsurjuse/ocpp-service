package com.electrahub.ocpp.exception;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
public class ChargePointNotConnectedException extends RuntimeException {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChargePointNotConnectedException.class);


    /**
     * Executes charge point not connected exception for `ChargePointNotConnectedException`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param message input consumed by ChargePointNotConnectedException.
     */
    public ChargePointNotConnectedException(String message) {
        super(message);
        LOGGER.info("CODEx_ENTRY_LOG: Entering ChargePointNotConnectedException#ChargePointNotConnectedException");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering ChargePointNotConnectedException#ChargePointNotConnectedException with debug context");
    }

    /**
     * Executes charge point not connected exception for `ChargePointNotConnectedException`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.exception`.
     * @param message input consumed by ChargePointNotConnectedException.
     * @param cause input consumed by ChargePointNotConnectedException.
     */
    public ChargePointNotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }

}
