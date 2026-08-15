package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ConnectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);
    private static final String CONNECTION_PREFIX = "ocpp:connection:";
    private static final String PROTOCOL_PREFIX = "ocpp:protocol:";
    private static final DefaultRedisScript<Long> DELETE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('del', KEYS[1]); redis.call('del', KEYS[2]); return 1 else return 0 end",
            Long.class
    );
    private static final DefaultRedisScript<Long> CLAIM_OR_REFRESH_IF_OWNER = new DefaultRedisScript<>(
            "local owner = redis.call('get', KEYS[1]); "
                    + "if not owner then "
                    + "local claimed = redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2], 'NX'); "
                    + "if claimed then "
                    + "redis.call('set', KEYS[2], ARGV[3], 'EX', ARGV[2]); return 1; "
                    + "end; owner = redis.call('get', KEYS[1]); end; "
                    + "if owner == ARGV[1] then "
                    + "redis.call('expire', KEYS[1], ARGV[2]); "
                    + "redis.call('set', KEYS[2], ARGV[3], 'EX', ARGV[2]); "
                    + "return 1 else return 0 end",
            Long.class
    );


    private final ConcurrentHashMap<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> localProtocols = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingConnections = new AtomicBoolean(true);
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
        this.nodeId = firstNonBlank(
                System.getenv("NODE_ID"),
                System.getenv("HOSTNAME"),
                "node-" + System.identityHashCode(this)
        );
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
            redisTemplate.opsForValue().set(connectionKey(normalizedChargePointId), nodeId, connectionMarkerTtl);
        } catch (DataAccessException ex) {
            log.warn("Unable to store Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
        log.debug("Registered connection for charge point: {} on node: {}", normalizedChargePointId, nodeId);
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
            removeOwnedMarker(normalizedChargePointId);
        } catch (DataAccessException ex) {
            log.warn("Unable to remove Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
        log.debug("Removed connection for charge point: {}", normalizedChargePointId);
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
            removeOwnedMarker(normalizedChargePointId);
        } catch (DataAccessException ex) {
            log.warn("Unable to remove Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
        log.debug("Removed current connection for charge point: {}", normalizedChargePointId);
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
        if (isLocallyConnected(chargePointId)) {
            return true;
        }
        return getOwnerNodeId(chargePointId).isPresent();
    }

    public boolean isLocallyConnected(String chargePointId) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        WebSocketSession session = localSessions.get(normalizedChargePointId);
        boolean connected = session != null && session.isOpen();
        if (connected) {
            claimOrRefreshConnectionMarker(normalizedChargePointId);
        }
        if (!connected && session != null) {
            localSessions.remove(normalizedChargePointId, session);
            localProtocols.remove(normalizedChargePointId);
            try {
                removeOwnedMarker(normalizedChargePointId);
            } catch (DataAccessException ex) {
                log.warn("Unable to remove stale Redis marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
            }
        }
        return connected;
    }

    /**
     * Returns true only when this process has an open local socket and still
     * owns the matching Redis route. The Redis check is fail-closed so an HTTP
     * request handled by another replica cannot durably claim a command that it
     * cannot send.
     */
    public boolean isLocalConnectionOwner(String chargePointId) {
        return connectionOwnership(chargePointId) == ConnectionOwnership.LOCAL_OWNER;
    }

    public ConnectionOwnership connectionOwnership(String chargePointId) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        WebSocketSession session = localSessions.get(normalizedChargePointId);
        boolean localSocketOpen = session != null && session.isOpen();
        if (!localSocketOpen && session != null) {
            localSessions.remove(normalizedChargePointId, session);
            localProtocols.remove(normalizedChargePointId);
            try {
                removeOwnedMarker(normalizedChargePointId);
            } catch (DataAccessException ex) {
                log.warn("Unable to remove stale Redis marker for charge point {}: {}",
                        normalizedChargePointId, ex.getMessage());
            }
        }

        if (localSocketOpen && claimOrRefreshConnectionMarker(normalizedChargePointId)) {
            return ConnectionOwnership.LOCAL_OWNER;
        }

        try {
            String recordedOwner = redisTemplate.opsForValue()
                    .get(connectionKey(normalizedChargePointId));
            if (recordedOwner == null || recordedOwner.isBlank()) {
                return localSocketOpen
                        ? ConnectionOwnership.ROUTE_INDETERMINATE
                        : ConnectionOwnership.OFFLINE;
            }
            return nodeId.equals(recordedOwner)
                    ? ConnectionOwnership.ROUTE_INDETERMINATE
                    : ConnectionOwnership.REMOTE_OWNER;
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve Redis connection owner for charge point {}: {}",
                    normalizedChargePointId, ex.getMessage());
            return ConnectionOwnership.ROUTE_INDETERMINATE;
        }
    }

    private boolean claimOrRefreshConnectionMarker(String normalizedChargePointId) {
        try {
            Long refreshed = redisTemplate.execute(
                    CLAIM_OR_REFRESH_IF_OWNER,
                    List.of(connectionKey(normalizedChargePointId), protocolKey(normalizedChargePointId)),
                    nodeId,
                    String.valueOf(connectionMarkerTtl.toSeconds()),
                    localProtocols.getOrDefault(normalizedChargePointId, "OCPP16J")
            );
            return Long.valueOf(1L).equals(refreshed);
        } catch (DataAccessException ex) {
            log.warn("Unable to claim or refresh Redis connection marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
            return false;
        }
    }

    public void setProtocol(String chargePointId, String protocol) {
        if (chargePointId == null || protocol == null || protocol.isBlank()) {
            return;
        }
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        localProtocols.put(normalizedChargePointId, protocol);
        try {
            redisTemplate.opsForValue().set(protocolKey(normalizedChargePointId), protocol, connectionMarkerTtl);
        } catch (DataAccessException ex) {
            log.warn("Unable to store Redis protocol marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
        }
    }

    public String getProtocol(String chargePointId) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        String localProtocol = localProtocols.get(normalizedChargePointId);
        if (localProtocol != null) {
            return localProtocol;
        }
        try {
            String distributedProtocol = redisTemplate.opsForValue().get(protocolKey(normalizedChargePointId));
            return distributedProtocol == null || distributedProtocol.isBlank() ? "OCPP16J" : distributedProtocol;
        } catch (DataAccessException ex) {
            log.warn("Unable to read Redis protocol marker for charge point {}: {}", normalizedChargePointId, ex.getMessage());
            return "OCPP16J";
        }
    }

    public Optional<String> getOwnerNodeId(String chargePointId) {
        String normalizedChargePointId = normalizeChargePointId(chargePointId);
        try {
            String owner = redisTemplate.opsForValue().get(connectionKey(normalizedChargePointId));
            return owner == null || owner.isBlank() ? Optional.empty() : Optional.of(owner);
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve Redis connection owner for charge point {}: {}", normalizedChargePointId, ex.getMessage());
            return Optional.empty();
        }
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

    public enum ConnectionOwnership {
        LOCAL_OWNER,
        REMOTE_OWNER,
        OFFLINE,
        ROUTE_INDETERMINATE
    }

    public boolean isAcceptingConnections() {
        return acceptingConnections.get();
    }

    public long beginDrain() {
        acceptingConnections.set(false);
        return getActiveConnectionCount();
    }

    public void closeAllForRestart() {
        acceptingConnections.set(false);
        localSessions.forEach((chargePointId, session) -> {
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVICE_RESTARTED);
                }
            } catch (IOException exception) {
                log.warn("Unable to close charge-point connection {} during drain: {}",
                        chargePointId, exception.getMessage());
            } finally {
                removeConnection(chargePointId, session);
            }
        });
    }

    private void removeOwnedMarker(String normalizedChargePointId) {
        redisTemplate.execute(
                DELETE_IF_OWNER,
                List.of(connectionKey(normalizedChargePointId), protocolKey(normalizedChargePointId)),
                nodeId
        );
    }

    private String connectionKey(String normalizedChargePointId) {
        return CONNECTION_PREFIX + normalizedChargePointId;
    }

    private String protocolKey(String normalizedChargePointId) {
        return PROTOCOL_PREFIX + normalizedChargePointId;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        throw new IllegalArgumentException("At least one node identifier candidate is required");
    }

    private String normalizeChargePointId(String chargePointId) {
        if (chargePointId == null) {
            return "";
        }
        return chargePointId.trim();
    }

}
