package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class HeartbeatHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatHandler.class);


    private final ConnectionManager connectionManager;
    private final OcppConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Instant> lastPersistedAt = new ConcurrentHashMap<>();
    private static final Duration PERSIST_INTERVAL = Duration.ofSeconds(60);

    public HeartbeatHandler(
            ConnectionManager connectionManager,
            OcppConnectionRepository connectionRepository) {
        this.connectionManager = connectionManager;
        this.connectionRepository = connectionRepository;
    }

    /**
     * Retrieves get action for `HeartbeatHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        LOGGER.info(" Entering HeartbeatHandler#getAction");
        LOGGER.debug(" Entering HeartbeatHandler#getAction with debug context");
        return "Heartbeat";
    }

    /**
     * Processes handle for `HeartbeatHandler`.
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
            Instant now = Instant.now();

            if (shouldPersist(chargePointId, now)) try {
                OcppConnection connection = connectionRepository.findByChargePointId(chargePointId)
                        .orElseGet(() -> OcppConnection.builder()
                                .chargePointId(chargePointId)
                                .connectedAt(now)
                                .build());
                connection.setLastHeartbeatAt(now);
                connection.setLastSeenAt(now);
                connection.setNodeId(connectionManager.getNodeId());
                connection.setOcppProtocol(connectionManager.getProtocol(chargePointId));
                connection.setDisconnectedAt(null);
                connection.setActive(true);
                connectionRepository.save(connection);
                lastPersistedAt.put(chargePointId, now);
                log.debug("Updated heartbeat for charge point: {}", chargePointId);
            } catch (DataAccessException ex) {
                log.warn("Unable to persist heartbeat for charge point {}: {}", chargePointId, ex.getMessage());
            }

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

    private boolean shouldPersist(String chargePointId, Instant now) {
        Instant previous = lastPersistedAt.get(chargePointId);
        return previous == null || previous.plus(PERSIST_INTERVAL).isBefore(now);
    }

}
