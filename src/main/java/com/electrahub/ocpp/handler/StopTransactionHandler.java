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
public class StopTransactionHandler implements OcppMessageHandler {

    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StopTransactionHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public String getAction() {
        return "StopTransaction";
    }

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
