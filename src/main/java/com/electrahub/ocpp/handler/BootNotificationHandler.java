package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.integration.StationServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class BootNotificationHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootNotificationHandler.class);


    private final StationServiceClient stationServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int heartbeatIntervalSeconds;

    /**
     * Executes boot notification handler for `BootNotificationHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param stationServiceClient input consumed by BootNotificationHandler.
     */
    public BootNotificationHandler(
            StationServiceClient stationServiceClient,
            @Value("${ocpp.heartbeat.interval-seconds:60}") int heartbeatIntervalSeconds
    ) {
        LOGGER.info(" Entering BootNotificationHandler#BootNotificationHandler");
        LOGGER.debug(" Entering BootNotificationHandler#BootNotificationHandler with debug context");
        this.stationServiceClient = stationServiceClient;
        this.heartbeatIntervalSeconds = Math.max(30, heartbeatIntervalSeconds);
    }

    /**
     * Retrieves get action for `BootNotificationHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "BootNotification";
    }

    /**
     * Processes handle for `BootNotificationHandler`.
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
            log.info("Processing BootNotification for charge point: {}", chargePointId);

            String chargePointVendor = payload.path("chargePointVendor").asText("Unknown");
            String chargePointModel = payload.path("chargePointModel").asText("Unknown");
            String chargePointSerialNumber = payload.path("chargePointSerialNumber").asText("");
            String firmwareVersion = payload.path("firmwareVersion").asText("");

            try {
                stationServiceClient.getStation(chargePointId);
            } catch (Exception ex) {
                log.warn("Station metadata lookup failed for {}; accepting BootNotification anyway: {}",
                    chargePointId, ex.getMessage());
            }

            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Accepted");
            response.put("currentTime", Instant.now().toString());
            response.put("interval", heartbeatIntervalSeconds);

            log.debug("BootNotification response: status=Accepted for {}", chargePointId);
            return response;
        } catch (Exception e) {
            log.error("Error handling BootNotification: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Pending");
            response.put("currentTime", Instant.now().toString());
            response.put("interval", heartbeatIntervalSeconds);
            return response;
        }
    }

}
