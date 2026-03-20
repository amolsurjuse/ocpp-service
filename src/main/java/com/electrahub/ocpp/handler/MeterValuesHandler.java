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
public class MeterValuesHandler implements OcppMessageHandler {

    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MeterValuesHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public String getAction() {
        return "MeterValues";
    }

    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            int connectorId = payload.path("connectorId").asInt();
            int transactionId = payload.path("transactionId").asInt();

            log.info("Processing meter values for transaction: {}", transactionId);

            // Call session service to store meter values
            sessionServiceClient.addMeterValues(transactionId, payload);

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("MeterValues response sent for transaction: {}", transactionId);
            return response;
        } catch (Exception e) {
            log.error("Error handling MeterValues: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
