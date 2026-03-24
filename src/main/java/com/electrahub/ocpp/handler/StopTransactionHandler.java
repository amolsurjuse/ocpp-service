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
public class StopTransactionHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StopTransactionHandler.class);


    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes stop transaction handler for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by StopTransactionHandler.
     */
    public StopTransactionHandler(SessionServiceClient sessionServiceClient) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering StopTransactionHandler#StopTransactionHandler");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering StopTransactionHandler#StopTransactionHandler with debug context");
        this.sessionServiceClient = sessionServiceClient;
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
            int meterStop = payload.path("meterStop").asInt();
            String timestamp = payload.path("timestamp").asText();
            String reason = payload.path("reason").asText("Local");

            log.info("Stopping transaction: {}, meter: {}", transactionId, meterStop);

            // Call session service to complete session
            sessionServiceClient.stopSession(
                transactionId,
                objectMapper.createObjectNode()
                    .put("meterId", meterStop)
                    .put("endTime", timestamp)
                    .put("reason", reason)
            );

            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.set("idTagInfo", idTagInfo);

            log.debug("StopTransaction response: transactionId={}", transactionId);
            return response;
        } catch (Exception e) {
            log.error("Error handling StopTransaction: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
