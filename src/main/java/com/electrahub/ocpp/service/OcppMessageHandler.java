package com.electrahub.ocpp.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface OcppMessageHandler {

    /**
     * Retrieves get action for `OcppMessageHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @return result produced by getAction.
     */
    String getAction();

    /**
     * Processes handle for `OcppMessageHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by handle.
     * @param payload input consumed by handle.
     * @return result produced by handle.
     */
    JsonNode handle(String chargePointId, JsonNode payload);

    /** Carries the charger-supplied JSON-RPC identity to durable handlers. */
    default JsonNode handle(String chargePointId, String sourceMessageId, JsonNode payload) {
        return handle(chargePointId, payload);
    }

}
