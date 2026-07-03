package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ConnectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);


    private final ConcurrentHashMap<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> localProtocols = new ConcurrentHashMap<>();
    private final RedisTemplate<String, String> redisTemplate;
    private final String nodeId;
    private final Duration connectionMarkerTtl;

    /**
     * Executes connection manager for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param RedisTemplate<String input consumed by ConnectionManager.
     * @param redisTemplate input consumed by ConnectionManager.
     */
    public ConnectionManager(
            RedisTemplate<String, String> redisTemplate,
            @Value("${app.ocpp.redis.connection-marker-ttl-seconds:120}") long connectionMarkerTtlSeconds
    ) {
        LOGGER.info(" Entering ConnectionManager#ConnectionManager");
        LOGGER.debug(" Entering ConnectionManager#ConnectionManager with debug context");
        this.redisTemplate = redisTemplate;
        this.nodeId = System.getenv().getOrDefault("NODE_ID", "node-" + System.identityHashCode(this));
        this.connectionMarkerTtl = Duration.ofSeconds(Math.max(30L, connectionMarkerTtlSeconds));
    }

    /**
     * Creates register connection for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param chargePointId input consumed by registerConnection.
     * @param session input consumed by registerConnection.
     */
    public void registerConnection(String chargePointId, WebSocketSession session) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        localSessions.put(normalizedChargePointId, session);
        try {
            redisTemplate.opsForValue().set("ocpp:connection:" + normalizedChargePointId, nodeId, connectionMarkerTtl);
        } catch (DataAccessException ex) {
            log.warn("Unable to store Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
        log.info("Registered connection for charge point: {} on node: {}", normalizedChargePointId, nodeId);
    }

    /**
     * Removes remove connection for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param chargePointId input consumed by removeConnection.
     */
    public void removeConnection(String chargePointId) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        localSessions.remove(normalizedChargePointId);
        localProtocols.remove(normalizedChargePointId);
        try {
            redisTemplate.delete("ocpp:connection:" + normalizedChargePointId);
        } catch (DataAccessException ex) {
            log.warn("Unable to remove Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
        log.info("Removed connection for charge point: {}", normalizedChargePointId);
    }

    public boolean removeConnection(String chargePointId, WebSocketSession session) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        boolean removed = localSessions.remove(normalizedChargePointId, session);
        if (!removed) {
            log.info("Skipped stale websocket close for charge point {}; a newer session is registered", normalizedChargePointId);
            return false;
        }
        localProtocols.remove(normalizedChargePointId);
        try {
            redisTemplate.delete("ocpp:connection:" + normalizedChargePointId);
        } catch (DataAccessException ex) {
            log.warn("Unable to remove Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
        log.info("Removed current connection for charge point: {}", normalizedChargePointId);
        return true;
    }

    /**
     * Retrieves get session for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param chargePointId input consumed by getSession.
     * @return result produced by getSession.
     */
    public WebSocketSession getSession(String chargePointId) {
        return localSessions.get(normalizeChargePointId(chargePointId));
    }

    public void sendMessage(String chargePointId, String payload) throws IOException {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        WebSocketSession session = localSessions.get(normalizedChargePointId);
        if (session == null || !session.isOpen()) {
            throw new IOException("WebSocket session is not open for charge point: " + normalizedChargePointId);
        }
        sendMessage(session, payload);
    }

    public void sendMessage(WebSocketSession session, String payload) throws IOException {
        synchronized (session) {
            if (!session.isOpen()) {
                throw new IOException("WebSocket session is not open");
            }
            session.sendMessage(new TextMessage(payload));
        }
    }

    /**
     * Executes is connected for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param chargePointId input consumed by isConnected.
     * @return result produced by isConnected.
     */
    public boolean isConnected(String chargePointId) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        WebSocketSession session = localSessions.get(normalizedChargePointId);
        boolean connected = session != null && session.isOpen();
        if (connected) {
            refreshConnectionMarker(normalizedChargePointId);
        }
        if (!connected && session != null) {
            localSessions.remove(normalizedChargePointId, session);
            localProtocols.remove(normalizedChargePointId);
            try {
                redisTemplate.delete("ocpp:connection:" + normalizedChargePointId);
            } catch (DataAccessException ex) {
                log.warn("Unable to remove stale Redis marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
            }
        }
        return connected;
    }

    private void refreshConnectionMarker(String normalizedChargePointId) {
        try {
            redisTemplate.expire("ocpp:connection:" + normalizedChargePointId, connectionMarkerTtl);
        } catch (DataAccessException ex) {
            log.warn("Unable to refresh Redis connection marker TTL for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
    }

    public void setProtocol(String chargePointId, String protocol) {
        if (chargePointId == null || protocol == null || protocol.isBlank()) {
            return;
        }
        localProtocols.put(normalizeChargePointId(chargePointId), protocol);
    }

    public String getProtocol(String chargePointId) {
        return localProtocols.getOrDefault(normalizeChargePointId(chargePointId), "OCPP16J");
    }

    /**
     * Retrieves get active connection count for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @return result produced by getActiveConnectionCount.
     */
    public long getActiveConnectionCount() {
        return localSessions.size();
    }

    /**
     * Retrieves get all connected charge points for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @return result produced by getAllConnectedChargePoints.
     */
    public Collection<String> getAllConnectedChargePoints() {
        return localSessions.keySet();
    }

    /**
     * Retrieves get node id for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @return result produced by getNodeId.
     */
    public String getNodeId() {
        return nodeId;
    }

    private String normalizeChargePointId(String chargePointId) {
        if (chargePointId == null) {
            return "";
        }
        return chargePointId.trim();
    }

}
