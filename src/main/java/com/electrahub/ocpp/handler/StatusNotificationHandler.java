package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.integration.StationServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatusNotificationHandler implements OcppMessageHandler {
    private final StationServiceClient stationServiceClient;
    private final SessionServiceClient sessionServiceClient;
    private final OcppTelemetryDispatcher telemetryDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes status notification handler for `StatusNotificationHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param stationServiceClient input consumed by StatusNotificationHandler.
     */
    public StatusNotificationHandler(
            StationServiceClient stationServiceClient,
            SessionServiceClient sessionServiceClient,
            OcppTelemetryDispatcher telemetryDispatcher
    ) {
        this.stationServiceClient = stationServiceClient;
        this.sessionServiceClient = sessionServiceClient;
        this.telemetryDispatcher = telemetryDispatcher;
    }

    /**
     * Retrieves get action for `StatusNotificationHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "StatusNotification";
    }

    /**
     * Processes handle for `StatusNotificationHandler`.
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
            int connectorId = payload.path("connectorId").asInt();
            String status = payload.path("status").asText();
            String errorCode = payload.path("errorCode").asText();
            String timestamp = payload.path("timestamp").asText();
            JsonNode txNode = payload.path("transactionId");
            Integer transactionId = (txNode.isMissingNode() || txNode.isNull()) ? null : txNode.asInt();
            boolean endSessionRequested = "EndSessionRequested".equalsIgnoreCase(payload.path("info").asText())
                    || payload.path("customData").path("endSessionRequested").asBoolean(false);

            log.debug("Connector status update: chargePoint={}, connector={}, status={}, errorCode={}",
                chargePointId, connectorId, status, errorCode);

            telemetryDispatcher.dispatchStatusNotification(chargePointId, connectorId, () -> {
                stationServiceClient.updateConnectorStatus(chargePointId, connectorId, status);
                sessionServiceClient.onStatusNotification(
                        chargePointId,
                        connectorId,
                        status,
                        errorCode,
                        timestamp,
                        transactionId,
                        endSessionRequested
                );
            });

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("StatusNotification response sent");
            return response;
        } catch (Exception e) {
            if (e instanceof com.electrahub.ocpp.messaging.OcppDeviceEventPublisher.OcppEventPublishException publishFailure) {
                throw publishFailure;
            }
            log.error("Error handling StatusNotification: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
