package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.StationServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class BootNotificationHandler implements OcppMessageHandler {

    private final StationServiceClient stationServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BootNotificationHandler(StationServiceClient stationServiceClient) {
        this.stationServiceClient = stationServiceClient;
    }

    @Override
    public String getAction() {
        return "BootNotification";
    }

    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            log.info("Processing BootNotification for charge point: {}", chargePointId);

            String chargePointVendor = payload.path("chargePointVendor").asText("Unknown");
            String chargePointModel = payload.path("chargePointModel").asText("Unknown");
            String chargePointSerialNumber = payload.path("chargePointSerialNumber").asText("");
            String firmwareVersion = payload.path("firmwareVersion").asText("");

            // Call station service to update station info
            stationServiceClient.getStation(chargePointId);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Accepted");
            response.put("currentTime", Instant.now().toString());
            response.put("interval", 900); // 15 minutes heartbeat interval

            log.debug("BootNotification response: status=Accepted for {}", chargePointId);
            return response;
        } catch (Exception e) {
            log.error("Error handling BootNotification: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Pending");
            response.put("currentTime", Instant.now().toString());
            response.put("interval", 900);
            return response;
        }
    }

}
