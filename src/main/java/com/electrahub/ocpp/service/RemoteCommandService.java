package com.electrahub.ocpp.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RemoteCommandService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCommandService.class);


    private final ConnectionManager connectionManager;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingResponses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ocpp.message.response-timeout-seconds:30}")
    private int responseTimeoutSeconds;

    /**
     * Executes remote command service for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param connectionManager input consumed by RemoteCommandService.
     */
    public RemoteCommandService(ConnectionManager connectionManager) {
        LOGGER.info(" Entering RemoteCommandService#RemoteCommandService");
        LOGGER.debug(" Entering RemoteCommandService#RemoteCommandService with debug context");
        this.connectionManager = connectionManager;
    }

    /**
     * Executes send command for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by sendCommand.
     * @param action input consumed by sendCommand.
     * @param payload input consumed by sendCommand.
     * @return result produced by sendCommand.
     */
    public CompletableFuture<JsonNode> sendCommand(String chargePointId, String action, JsonNode payload) {
        if (!connectionManager.isConnected(chargePointId)) {
            throw new ChargePointNotConnectedException("Charge point not connected: " + chargePointId);
        }

        String messageId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        OcppJsonRpcMessage message = OcppJsonRpcMessage.createCall(messageId, action, payload);
        String messageJson = message.toJson();

        pendingResponses.put(messageId, future);

        try {
            connectionManager.sendMessage(chargePointId, messageJson);
            log.info("Sent command to {}: action={}, messageId={}", chargePointId, action, messageId);

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

    /**
     * Executes resolve pending response for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param messageId input consumed by resolvePendingResponse.
     * @param payload input consumed by resolvePendingResponse.
     */
    public void resolvePendingResponse(String messageId, JsonNode payload) {
        CompletableFuture<JsonNode> future = pendingResponses.remove(messageId);
        if (future != null) {
            future.complete(payload);
            log.debug("Resolved pending response: messageId={}", messageId);
        } else {
            log.warn("No pending response found for messageId: {}", messageId);
        }
    }

    /**
     * Executes reject pending response for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param messageId input consumed by rejectPendingResponse.
     * @param errorCode input consumed by rejectPendingResponse.
     * @param errorDescription input consumed by rejectPendingResponse.
     */
    public void rejectPendingResponse(String messageId, String errorCode, String errorDescription) {
        CompletableFuture<JsonNode> future = pendingResponses.remove(messageId);
        if (future != null) {
            future.completeExceptionally(new RuntimeException(errorCode + ": " + errorDescription));
            log.warn("Rejected pending response: messageId={}, errorCode={}", messageId, errorCode);
        }
    }

    /**
     * Executes remote start transaction for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by remoteStartTransaction.
     * @param idTag input consumed by remoteStartTransaction.
     * @param connectorId input consumed by remoteStartTransaction.
     * @return result produced by remoteStartTransaction.
     */
    public CompletableFuture<JsonNode> remoteStartTransaction(String chargePointId, String idTag, Integer connectorId) {
        if (isOcpp201(chargePointId)) {
            ObjectNode idToken = objectMapper.createObjectNode();
            idToken.put("idToken", idTag);
            idToken.put("type", "Central");

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("remoteStartId", Math.abs(UUID.randomUUID().hashCode()));
            payload.set("idToken", idToken);
            if (connectorId != null) {
                payload.put("evseId", connectorId);
            }
            return sendCommand(chargePointId, "RequestStartTransaction", payload);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("idTag", idTag);
        if (connectorId != null) {
            payload.put("connectorId", connectorId);
        }
        return sendCommand(chargePointId, "RemoteStartTransaction", payload);
    }

    /**
     * Executes remote stop transaction for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by remoteStopTransaction.
     * @param transactionId input consumed by remoteStopTransaction.
     * @return result produced by remoteStopTransaction.
     */
    public CompletableFuture<JsonNode> remoteStopTransaction(String chargePointId, Integer transactionId) {
        if (isOcpp201(chargePointId)) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("transactionId", String.valueOf(transactionId));
            return sendCommand(chargePointId, "RequestStopTransaction", payload);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("transactionId", transactionId);
        return sendCommand(chargePointId, "RemoteStopTransaction", payload);
    }

    private boolean isOcpp201(String chargePointId) {
        return "OCPP201".equalsIgnoreCase(connectionManager.getProtocol(chargePointId));
    }

    /**
     * Executes reset for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by reset.
     * @param type input consumed by reset.
     * @return result produced by reset.
     */
    public CompletableFuture<JsonNode> reset(String chargePointId, String type) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", type); // "Hard" or "Soft"
        return sendCommand(chargePointId, "Reset", payload);
    }

    /**
     * Executes unlock connector for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by unlockConnector.
     * @param connectorId input consumed by unlockConnector.
     * @return result produced by unlockConnector.
     */
    public CompletableFuture<JsonNode> unlockConnector(String chargePointId, Integer connectorId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("connectorId", connectorId);
        return sendCommand(chargePointId, "UnlockConnector", payload);
    }

    /**
     * Updates set charging profile for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by setChargingProfile.
     * @param connectorId input consumed by setChargingProfile.
     * @param chargingProfile input consumed by setChargingProfile.
     * @return result produced by setChargingProfile.
     */
    public CompletableFuture<JsonNode> setChargingProfile(String chargePointId, Integer connectorId, JsonNode chargingProfile) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("connectorId", connectorId);
        payload.set("csChargingProfiles", chargingProfile);
        return sendCommand(chargePointId, "SetChargingProfile", payload);
    }

    /**
     * Executes change configuration for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by changeConfiguration.
     * @param key input consumed by changeConfiguration.
     * @param value input consumed by changeConfiguration.
     * @return result produced by changeConfiguration.
     */
    public CompletableFuture<JsonNode> changeConfiguration(String chargePointId, String key, String value) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("key", key);
        payload.put("value", value);
        return sendCommand(chargePointId, "ChangeConfiguration", payload);
    }

    /**
     * Retrieves get configuration for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by getConfiguration.
     * @param keys input consumed by getConfiguration.
     * @return result produced by getConfiguration.
     */
    public CompletableFuture<JsonNode> getConfiguration(String chargePointId, JsonNode keys) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("key", keys);
        return sendCommand(chargePointId, "GetConfiguration", payload);
    }

    /**
     * Executes trigger message for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by triggerMessage.
     * @param requestedMessage input consumed by triggerMessage.
     * @return result produced by triggerMessage.
     */
    public CompletableFuture<JsonNode> triggerMessage(String chargePointId, String requestedMessage) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("requestedMessage", requestedMessage);
        return sendCommand(chargePointId, "TriggerMessage", payload);
    }

}
