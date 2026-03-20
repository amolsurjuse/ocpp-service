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
public class StartTransactionHandler implements OcppMessageHandler {

    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StartTransactionHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public String getAction() {
        return "StartTransaction";
    }

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
