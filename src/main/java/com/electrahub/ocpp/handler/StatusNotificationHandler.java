package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.integration.StationServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatusNotificationHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusNotificationHandler.class);


    private final StationServiceClient stationServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes status notification handler for `StatusNotificationHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param stationServiceClient input consumed by StatusNotificationHandler.
     */
    public StatusNotificationHandler(StationServiceClient stationServiceClient) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering StatusNotificationHandler#StatusNotificationHandler");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering StatusNotificationHandler#StatusNotificationHandler with debug context");
        this.stationServiceClient = stationServiceClient;
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

            log.info("Connector status update: chargePoint={}, connector={}, status={}, errorCode={}",
                chargePointId, connectorId, status, errorCode);

            // Call station service to update connector status
            stationServiceClient.updateConnectorStatus(chargePointId, connectorId, status);

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("StatusNotification response sent");
            return response;
        } catch (Exception e) {
            log.error("Error handling StatusNotification: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
