package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StopTransactionHandler implements OcppMessageHandler {
    private final SessionServiceClient sessionServiceClient;
    private final OcppTelemetryDispatcher callbackDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes stop transaction handler for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by StopTransactionHandler.
     */
    public StopTransactionHandler(
            SessionServiceClient sessionServiceClient,
            OcppTelemetryDispatcher callbackDispatcher
    ) {
        this.sessionServiceClient = sessionServiceClient;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * Retrieves get action for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "StopTransaction";
    }

    /**
     * Processes handle for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param chargePointId input consumed by handle.
     * @param payload input consumed by handle.
     * @return result produced by handle.
     */
    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            int transactionId = payload.path("transactionId").asInt();
            int connectorId = payload.path("connectorId").asInt(0);
            int meterStop = payload.path("meterStop").asInt();
            String timestamp = payload.path("timestamp").asText();
            String reason = payload.path("reason").asText("Local");

            log.info("Stopping transaction: {}, meter: {}", transactionId, meterStop);

            Integer resolvedConnectorId = connectorId > 0 ? connectorId : null;
            boolean queued = callbackDispatcher.dispatchStopTransaction(chargePointId, resolvedConnectorId, () ->
                    sessionServiceClient.onStopTransaction(
                            transactionId,
                            chargePointId,
                            resolvedConnectorId,
                            meterStop,
                            timestamp,
                            reason
                    )
            );
            if (!queued) {
                // A stop changes financial settlement. Queue exhaustion is exceptional;
                // synchronously persisting it is safer than dropping the terminal event.
                sessionServiceClient.onStopTransaction(
                        transactionId,
                        chargePointId,
                        resolvedConnectorId,
                        meterStop,
                        timestamp,
                        reason
                );
            }

            ObjectNode response = objectMapper.createObjectNode();

            log.debug("StopTransaction response: transactionId={}", transactionId);
            return response;
        } catch (Exception e) {
            if (e instanceof com.electrahub.ocpp.messaging.OcppDeviceEventPublisher.OcppEventPublishException publishFailure) {
                throw publishFailure;
            }
            log.error("Error handling StopTransaction: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
