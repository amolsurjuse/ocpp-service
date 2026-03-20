package com.electrahub.ocpp.service;

import com.electrahub.ocpp.exception.ChargePointNotConnectedException;
import com.electrahub.ocpp.exception.OcppCommandTimeoutException;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.electrahub.ocpp.websocket.OcppJsonRpcMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RemoteCommandService {

    private final ConnectionManager connectionManager;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingResponses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ocpp.message.response-timeout-seconds:30}")
    private int responseTimeoutSeconds;

    public RemoteCommandService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public CompletableFuture<JsonNode> sendCommand(String chargePointId, String action, JsonNode payload) {
        if (!connectionManager.isConnected(chargePointId)) {
            throw new ChargePointNotConnectedException("Charge point not connected: " + chargePointId);
        }

        String messageId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        OcppJsonRpcMessage message = OcppJsonRpcMessage.createCall(messageId, action, payload);
        String messageJson = message.toJson();

        try {
            var session = connectionManager.getSession(chargePointId);
            session.sendMessage(new TextMessage(messageJson));
            log.info("Sent command to {}: action={}, messageId={}", chargePointId, action, messageId);

            pendingResponses.put(messageId, future);

            future.orTimeout(responseTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        log.warn("Command timeout for {}: action={}, messageId={}", chargePointId, action, messageId);
                        throw new OcppCommandTimeoutException("Command timeout: " + action);
                    }
                    throw new RuntimeException(throwable);
                });

        } catch (IOException e) {
            log.error("Error sending command to {}: {}", chargePointId, e.getMessage(), e);
            pendingResponses.remove(messageId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public void resolvePendingResponse(String messageId, JsonNode payload) {
        CompletableFuture<JsonNode> future = pendingResponses.remove(messageId);
        if (future != null) {
            future.complete(payload);
            log.debug("Resolved pending response: messageId={}", messageId);
        } else {
            log.warn("No pending response found for messageId: {}", messageId);
        }
    }

    public void rejectPendingResponse(String messageId, String errorCode, String errorDescription) {
        CompletableFuture<JsonNode> future = pendingResponses.remove(messageId);
        if (future != null) {
            future.completeExceptionally(new RuntimeException(errorCode + ": " + errorDescription));
            log.warn("Rejected pending response: messageId={}, errorCode={}", messageId, errorCode);
        }
    }

    public CompletableFuture<JsonNode> remoteStartTransaction(String chargePointId, String idTag, Integer connectorId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("idTag", idTag);
        if (connectorId != null) {
            payload.put("connectorId", connectorId);
        }
        return sendCommand(chargePointId, "RemoteStartTransaction", payload);
    }

    public CompletableFuture<JsonNode> remoteStopTransaction(String chargePointId, Integer transactionId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("transactionId", transactionId);
        return sendCommand(chargePointId, "RemoteStopTransaction", payload);
    }

    public CompletableFuture<JsonNode> reset(String chargePointId, String type) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", type); // "Hard" or "Soft"
        return sendCommand(chargePointId, "Reset", payload);
    }

    public CompletableFuture<JsonNode> unlockConnector(String chargePointId, Integer connectorId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("connectorId", connectorId);
        return sendCommand(chargePointId, "UnlockConnector", payload);
    }

    public CompletableFuture<JsonNode> setChargingProfile(String chargePointId, Integer connectorId, JsonNode chargingProfile) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("connectorId", connectorId);
        payload.set("csChargingProfiles", chargingProfile);
        return sendCommand(chargePointId, "SetChargingProfile", payload);
    }

    public CompletableFuture<JsonNode> changeConfiguration(String chargePointId, String key, String value) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("key", key);
        payload.put("value", value);
        return sendCommand(chargePointId, "ChangeConfiguration", payload);
    }

    public CompletableFuture<JsonNode> getConfiguration(String chargePointId, JsonNode keys) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("key", keys);
        return sendCommand(chargePointId, "GetConfiguration", payload);
    }

    public CompletableFuture<JsonNode> triggerMessage(String chargePointId, String requestedMessage) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("requestedMessage", requestedMessage);
        return sendCommand(chargePointId, "TriggerMessage", payload);
    }

}
