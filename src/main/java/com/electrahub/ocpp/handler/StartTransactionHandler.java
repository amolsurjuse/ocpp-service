package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartTransactionHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartTransactionHandler.class);


    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates start transaction handler for `StartTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by StartTransactionHandler.
     */
    public StartTransactionHandler(SessionServiceClient sessionServiceClient) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering StartTransactionHandler#StartTransactionHandler");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering StartTransactionHandler#StartTransactionHandler with debug context");
        this.sessionServiceClient = sessionServiceClient;
    }

    /**
     * Retrieves get action for `StartTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "StartTransaction";
    }

    /**
     * Processes handle for `StartTransactionHandler`.
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
            String idTag = payload.path("idTag").asText();
            int meterStart = payload.path("meterStart").asInt();
            String timestamp = payload.path("timestamp").asText();

            log.info("Starting transaction for charge point: {}, connector: {}, idTag: {}",
                chargePointId, connectorId, idTag);

            // Call session service to create session
            JsonNode sessionResponse = sessionServiceClient.startSession(
                objectMapper.createObjectNode()
                    .put("chargePointId", chargePointId)
                    .put("connectorId", connectorId)
                    .put("idTag", idTag)
                    .put("meterStart", meterStart)
                    .put("startTime", timestamp)
            );

            int transactionId = sessionResponse.path("sessionId").asInt();

            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.put("transactionId", transactionId);
            response.set("idTagInfo", idTagInfo);

            log.debug("StartTransaction response: transactionId={}", transactionId);
            return response;
        } catch (Exception e) {
            log.error("Error handling StartTransaction: {}", e.getMessage(), e);
            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Invalid");
            ObjectNode response = objectMapper.createObjectNode();
            response.put("transactionId", 0);
            response.set("idTagInfo", idTagInfo);
            return response;
        }
    }

}
