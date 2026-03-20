package com.electrahub.ocpp.handler;

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

    private final StationServiceClient stationServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StatusNotificationHandler(StationServiceClient stationServiceClient) {
        this.stationServiceClient = stationServiceClient;
    }

    @Override
    public String getAction() {
        return "StatusNotification";
    }

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
