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
public class TransactionEventHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionEventHandler.class);


    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes transaction event handler for `TransactionEventHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by TransactionEventHandler.
     */
    public TransactionEventHandler(SessionServiceClient sessionServiceClient) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering TransactionEventHandler#TransactionEventHandler");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering TransactionEventHandler#TransactionEventHandler with debug context");
        this.sessionServiceClient = sessionServiceClient;
    }

    /**
     * Retrieves get action for `TransactionEventHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "TransactionEvent";
    }

    /**
     * Processes handle for `TransactionEventHandler`.
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
            String eventType = payload.path("eventType").asText();
            String timestamp = payload.path("timestamp").asText();
            String triggerReason = payload.path("triggerReason").asText();
            int seqNo = payload.path("seqNo").asInt();

            log.info("Transaction event from {}: eventType={}, trigger={}", chargePointId, eventType, triggerReason);

            JsonNode transactionInfo = payload.path("transactionInfo");
            int transactionId = transactionInfo.path("transactionId").asInt();

            switch (eventType) {
                case "Started" -> {
                    ObjectNode startPayload = objectMapper.createObjectNode()
                            .put("chargePointId", chargePointId)
                            .put("timestamp", timestamp);
                    startPayload.set("transactionInfo", transactionInfo);
                    sessionServiceClient.startSession(
                            startPayload
                    );
                }
                case "Updated" -> {
                    sessionServiceClient.addMeterValues(transactionId, payload);
                }
                case "Ended" -> {
                    sessionServiceClient.stopSession(
                        transactionId,
                        objectMapper.createObjectNode()
                            .put("timestamp", timestamp)
                            .put("reason", triggerReason)
                    );
                }
            }

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("TransactionEvent response sent for eventType: {}", eventType);
            return response;
        } catch (Exception e) {
            log.error("Error handling TransactionEvent: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
