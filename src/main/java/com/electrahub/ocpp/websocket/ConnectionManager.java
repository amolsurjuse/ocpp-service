package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ConnectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);


    private final ConcurrentHashMap<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();
    private final RedisTemplate<String, String> redisTemplate;
    private final String nodeId;

    /**
     * Executes connection manager for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param RedisTemplate<String input consumed by ConnectionManager.
     * @param redisTemplate input consumed by ConnectionManager.
     */
    public ConnectionManager(RedisTemplate<String, String> redisTemplate) {
        LOGGER.info(" Entering ConnectionManager#ConnectionManager");
        LOGGER.debug(" Entering ConnectionManager#ConnectionManager with debug context");
        this.redisTemplate = redisTemplate;
        this.nodeId = System.getenv().getOrDefault("NODE_ID", "node-" + System.identityHashCode(this));
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
        localSessions.put(chargePointId, session);
        try {
            redisTemplate.opsForValue().set("ocpp:connection:" + chargePointId, nodeId);
        } catch (DataAccessException ex) {
            log.warn("Unable to store Redis connection marker for charge point {}: {}", chargePointId, ex.getMessage());
        }
        log.info("Registered connection for charge point: {} on node: {}", chargePointId, nodeId);
    }

    /**
     * Removes remove connection for `ConnectionManager`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param chargePointId input consumed by removeConnection.
     */
    public void removeConnection(String chargePointId) {
        localSessions.remove(chargePointId);
        try {
            redisTemplate.delete("ocpp:connection:" + chargePointId);
        } catch (DataAccessException ex) {
            log.warn("Unable to remove Redis connection marker for charge point {}: {}", chargePointId, ex.getMessage());
        }
        log.info("Removed connection for charge point: {}", chargePointId);
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
        return localSessions.get(chargePointId);
    }

    public void sendMessage(String chargePointId, String payload) throws IOException {
        WebSocketSession session = localSessions.get(chargePointId);
        if (session == null || !session.isOpen()) {
            throw new IOException("WebSocket session is not open for charge point: " + chargePointId);
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
        WebSocketSession session = localSessions.get(chargePointId);
        return session != null && session.isOpen();
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

}
