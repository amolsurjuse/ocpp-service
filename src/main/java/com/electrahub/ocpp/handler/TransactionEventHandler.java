package com.electrahub.ocpp.handler;

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

    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransactionEventHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public String getAction() {
        return "TransactionEvent";
    }

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
                    sessionServiceClient.startSession(
                        objectMapper.createObjectNode()
                            .put("chargePointId", chargePointId)
                            .set("transactionInfo", transactionInfo)
                            .put("timestamp", timestamp)
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
