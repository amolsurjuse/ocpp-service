package com.electrahub.ocpp.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ConnectionManager {

    private final ConcurrentHashMap<String, WebSocketSession> localSessions = new ConcurrentHashMap<>();
    private final RedisTemplate<String, String> redisTemplate;
    private final String nodeId;

    public ConnectionManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.nodeId = System.getenv().getOrDefault("NODE_ID", "node-" + System.identityHashCode(this));
    }

    public void registerConnection(String chargePointId, WebSocketSession session) {
        localSessions.put(chargePointId, session);
        // Store in Redis for multi-node awareness
        redisTemplate.opsForValue().set("ocpp:connection:" + chargePointId, nodeId);
        log.info("Registered connection for charge point: {} on node: {}", chargePointId, nodeId);
    }

    public void removeConnection(String chargePointId) {
        localSessions.remove(chargePointId);
        redisTemplate.delete("ocpp:connection:" + chargePointId);
        log.info("Removed connection for charge point: {}", chargePointId);
    }

    public WebSocketSession getSession(String chargePointId) {
        return localSessions.get(chargePointId);
    }

    public boolean isConnected(String chargePointId) {
        return localSessions.containsKey(chargePointId);
    }

    public long getActiveConnectionCount() {
        return localSessions.size();
    }

    public Collection<String> getAllConnectedChargePoints() {
        return localSessions.keySet();
    }

    public String getNodeId() {
        return nodeId;
    }

}
