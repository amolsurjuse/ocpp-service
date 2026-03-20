package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class HeartbeatHandler implements OcppMessageHandler {

    private final ConnectionManager connectionManager;
    private final OcppConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HeartbeatHandler(
            ConnectionManager connectionManager,
            OcppConnectionRepository connectionRepository) {
        this.connectionManager = connectionManager;
        this.connectionRepository = connectionRepository;
    }

    @Override
    public String getAction() {
        return "Heartbeat";
    }

    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            Instant now = Instant.now();

            // Update last heartbeat in database
            connectionRepository.findByChargePointId(chargePointId).ifPresent(connection -> {
                connection.setLastHeartbeatAt(now);
                connectionRepository.save(connection);
                log.debug("Updated heartbeat for charge point: {}", chargePointId);
            });

            ObjectNode response = objectMapper.createObjectNode();
            response.put("currentTime", now.toString());

            log.debug("Heartbeat response sent for: {}", chargePointId);
            return response;
        } catch (Exception e) {
            log.error("Error handling Heartbeat: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("currentTime", Instant.now().toString());
            return response;
        }
    }

}
