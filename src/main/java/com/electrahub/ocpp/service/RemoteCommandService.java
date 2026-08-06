package com.electrahub.ocpp.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.OcppRemoteStartCommand;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandOutcome;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandState;
import com.electrahub.ocpp.exception.ChargePointNotConnectedException;
import com.electrahub.ocpp.exception.ChargePointRouteUnavailableException;
import com.electrahub.ocpp.exception.OcppProtocolException;
import com.electrahub.ocpp.web.dto.RemoteStartCommandStatusResponse;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.electrahub.ocpp.websocket.OcppJsonRpcMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
public class RemoteCommandService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCommandService.class);
    private static final Pattern COMMAND_KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}");


    private final ConnectionManager connectionManager;
    private final OcppAuthorizationGrantService authorizationGrants;
    private final RemoteStartCommandStore remoteStartCommands;
    private final IdTagFingerprintService idTagFingerprints;
    private final OcppClusterCommandRouter clusterCommandRouter;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingResponses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int responseTimeoutSeconds;

    /**
     * Executes remote command service for `RemoteCommandService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param connectionManager input consumed by RemoteCommandService.
     */
    public RemoteCommandService(
            ConnectionManager connectionManager,
            OcppAuthorizationGrantService authorizationGrants,
            RemoteStartCommandStore remoteStartCommands,
            IdTagFingerprintService idTagFingerprints,
            MeterRegistry meterRegistry,
            OcppClusterCommandRouter clusterCommandRouter,
            @Value("${ocpp.message.response-timeout-seconds:30}") int responseTimeoutSeconds
    ) {
        LOGGER.info(" Entering RemoteCommandService#RemoteCommandService");
        LOGGER.debug(" Entering RemoteCommandService#RemoteCommandService with debug context");
        this.connectionManager = connectionManager;
        this.authorizationGrants = authorizationGrants;
        this.remoteStartCommands = remoteStartCommands;
        this.idTagFingerprints = idTagFingerprints;
        this.meterRegistry = meterRegistry;
        this.responseTimeoutSeconds = Math.max(1, responseTimeoutSeconds);
        this.clusterCommandRouter = clusterCommandRouter;
        Gauge.builder("electrahub.ocpp.remote_command.pending", pendingResponses, responses -> responses.size())
                .description("OCPP commands awaiting a charge-point response")
                .register(meterRegistry);
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
        return sendCommand(chargePointId, action, payload, UUID.randomUUID().toString());
    }

    private CompletableFuture<JsonNode> sendCommand(
            String chargePointId,
            String action,
            JsonNode payload,
            String messageId
    ) {
        ConnectionManager.ConnectionOwnership ownership = connectionManager.connectionOwnership(chargePointId);
        if (ownership == ConnectionManager.ConnectionOwnership.LOCAL_OWNER) {
            return sendCommandLocally(chargePointId, action, payload, messageId);
        }
        if (ownership == ConnectionManager.ConnectionOwnership.OFFLINE) {
            throw new ChargePointNotConnectedException("Charge point not connected: " + chargePointId);
        }
        if (ownership == ConnectionManager.ConnectionOwnership.ROUTE_INDETERMINATE) {
            throw new ChargePointRouteUnavailableException(
                    "Unable to determine the owning OCPP node; retry the request");
        }

        String ownerNodeId = connectionManager.getOwnerNodeId(chargePointId)
                .orElseThrow(() -> new ChargePointRouteUnavailableException(
                        "The owning OCPP node is no longer available; retry the request"));
        if (!clusterCommandRouter.isEnabled()) {
            throw new ChargePointRouteUnavailableException(
                    "Charge point connection is owned by another OCPP node; cluster routing is disabled");
        }
        log.info("Routing command for {} to owning OCPP node {}: action={}",
                chargePointId, ownerNodeId, action);
        return clusterCommandRouter.route(ownerNodeId, chargePointId, action, payload, messageId);
    }

    public CompletableFuture<JsonNode> sendCommandLocally(String chargePointId, String action, JsonNode payload) {
        return sendCommandLocally(chargePointId, action, payload, UUID.randomUUID().toString());
    }

    public CompletableFuture<JsonNode> sendCommandLocally(
            String chargePointId,
            String action,
            JsonNode payload,
            String messageId
    ) {
        if (!connectionManager.isLocalConnectionOwner(chargePointId)) {
            throw new ChargePointNotConnectedException("Charge point not connected on this node: " + chargePointId);
        }
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        Timer.Sample commandTimer = Timer.start(meterRegistry);

        OcppJsonRpcMessage message = OcppJsonRpcMessage.createCall(messageId, action, payload);
        String messageJson = message.toJson();

        pendingResponses.put(messageId, future);

        future.orTimeout(responseTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((ignored, throwable) -> {
                    pendingResponses.remove(messageId, future);
                    commandTimer.stop(Timer.builder("electrahub.ocpp.remote_command.duration")
                            .description("Time from an OCPP command send to a terminal response")
                            .publishPercentileHistogram()
                            .tag("action", action)
                            .tag("outcome", commandOutcome(throwable))
                            .register(meterRegistry));
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        log.warn("Command timeout for {}: action={}, messageId={}", chargePointId, action, messageId);
                    }
                });

        try {
            connectionManager.sendMessage(chargePointId, messageJson);
            log.info("Sent command to {}: action={}, messageId={}", chargePointId, action, messageId);
        } catch (IOException e) {
            log.error("Error sending command to {}: {}", chargePointId, e.getMessage(), e);
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
            future.completeExceptionally(new OcppCallErrorException(errorCode + ": " + errorDescription));
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
        return remoteStartTransaction(chargePointId, idTag, connectorId, null, null);
    }

    public CompletableFuture<JsonNode> remoteStartTransaction(
            String chargePointId, String idTag, Integer connectorId, String correlationId) {
        return remoteStartTransaction(chargePointId, idTag, connectorId, correlationId, null);
    }

    public CompletableFuture<JsonNode> remoteStartTransaction(
            String chargePointId,
            String idTag,
            Integer connectorId,
            String correlationId,
            String commandKey
    ) {
        String normalizedCommandKey = normalizeCommandKey(commandKey);
        if (normalizedCommandKey == null) {
            return executeLegacyRemoteStart(chargePointId, idTag, connectorId, correlationId);
        }

        ConnectionManager.ConnectionOwnership connectionOwnership =
                connectionManager.connectionOwnership(chargePointId);
        if (connectionOwnership == ConnectionManager.ConnectionOwnership.OFFLINE) {
            throw new ChargePointNotConnectedException(
                    "Charge point not connected: " + chargePointId);
        }
        if (connectionOwnership == ConnectionManager.ConnectionOwnership.ROUTE_INDETERMINATE
                || (connectionOwnership == ConnectionManager.ConnectionOwnership.REMOTE_OWNER
                && !clusterCommandRouter.isEnabled())) {
            throw new ChargePointRouteUnavailableException(
                    "Charge point connection is owned by another OCPP node; retry the request");
        }

        String protocol = connectionManager.getProtocol(chargePointId);
        Integer remoteStartId = isOcpp201Protocol(protocol)
                ? deterministicRemoteStartId(normalizedCommandKey)
                : null;
        RemoteStartCommandStore.Claim claim = remoteStartCommands.claim(
                new RemoteStartCommandStore.CommandSpec(
                        normalizedCommandKey,
                        normalizeOptional(correlationId),
                        chargePointId,
                        idTagFingerprints.fingerprint(idTag),
                        connectorId,
                        protocol,
                        UUID.randomUUID().toString(),
                        remoteStartId
                ),
                Duration.ofSeconds(responseTimeoutSeconds)
        );

        if (!claim.claimed()) {
            return CompletableFuture.completedFuture(replayPayload(claim.command()));
        }

        return executeClaimedRemoteStart(claim.command(), idTag);
    }

    public Optional<RemoteStartCommandStatusResponse> remoteStartStatus(String commandKey) {
        String normalizedCommandKey = normalizeCommandKey(commandKey);
        if (normalizedCommandKey == null) {
            return Optional.empty();
        }
        return remoteStartCommands.find(normalizedCommandKey).map(this::statusResponse);
    }

    private CompletableFuture<JsonNode> executeLegacyRemoteStart(
            String chargePointId,
            String idTag,
            Integer connectorId,
            String correlationId
    ) {
        if (!authorizationGrants.grantRemoteStart(chargePointId, connectorId, idTag, correlationId)) {
            throw new IllegalStateException("Unable to create the remote-start authorization grant");
        }
        CompletableFuture<JsonNode> command;
        try {
            command = sendRemoteStartCommand(chargePointId, idTag, connectorId, null, null);
        } catch (RuntimeException exception) {
            authorizationGrants.revokeRemoteStart(chargePointId, connectorId, idTag);
            throw exception;
        }
        return command.whenComplete((response, error) -> {
            if (error != null || !acceptedRemoteStart(response)) {
                authorizationGrants.revokeRemoteStart(chargePointId, connectorId, idTag);
            }
        });
    }

    private CompletableFuture<JsonNode> executeClaimedRemoteStart(
            OcppRemoteStartCommand durableCommand,
            String idTag
    ) {
        String chargePointId = durableCommand.getChargePointId();
        Integer connectorId = durableCommand.getConnectorId();

        if (!authorizationGrants.grantRemoteStart(
                chargePointId,
                connectorId,
                idTag,
                durableCommand.getCorrelationId())) {
            OcppRemoteStartCommand failed = remoteStartCommands.complete(
                    durableCommand,
                    RemoteStartCommandState.TERMINAL,
                    RemoteStartCommandOutcome.FAILED,
                    null,
                    "Unable to create the remote-start authorization grant"
            );
            return CompletableFuture.completedFuture(commandStatePayload(failed, false));
        }

        CompletableFuture<JsonNode> command;
        try {
            command = sendRemoteStartCommand(
                    chargePointId,
                    idTag,
                    connectorId,
                    durableCommand.getRemoteStartId(),
                    durableCommand.getMessageId()
            );
        } catch (RuntimeException exception) {
            authorizationGrants.revokeRemoteStart(chargePointId, connectorId, idTag);
            OcppRemoteStartCommand failed = remoteStartCommands.complete(
                    durableCommand,
                    RemoteStartCommandState.TERMINAL,
                    RemoteStartCommandOutcome.FAILED,
                    null,
                    exception.getMessage()
            );
            return CompletableFuture.completedFuture(commandStatePayload(failed, false));
        }

        return command.handle((response, error) -> {
            if (error != null) {
                authorizationGrants.revokeRemoteStart(chargePointId, connectorId, idTag);
                Throwable cause = unwrap(error);
                boolean knownFailure = cause instanceof OcppCallErrorException
                        || cause instanceof ChargePointNotConnectedException;
                OcppRemoteStartCommand completed = remoteStartCommands.complete(
                        durableCommand,
                        knownFailure ? RemoteStartCommandState.TERMINAL : RemoteStartCommandState.UNKNOWN,
                        knownFailure ? RemoteStartCommandOutcome.FAILED : null,
                        null,
                        cause.getClass().getSimpleName() + ": " + cause.getMessage()
                );
                return commandStatePayload(completed, false);
            }

            boolean accepted = acceptedRemoteStart(response);
            if (!accepted) {
                authorizationGrants.revokeRemoteStart(chargePointId, connectorId, idTag);
            }
            JsonNode decorated = decorateResponse(
                    response,
                    durableCommand,
                    accepted ? RemoteStartCommandOutcome.ACCEPTED : RemoteStartCommandOutcome.REJECTED,
                    false
            );
            OcppRemoteStartCommand completed = remoteStartCommands.complete(
                    durableCommand,
                    RemoteStartCommandState.TERMINAL,
                    accepted ? RemoteStartCommandOutcome.ACCEPTED : RemoteStartCommandOutcome.REJECTED,
                    decorated.toString(),
                    null
            );

            if (completed.getState() != RemoteStartCommandState.TERMINAL
                    || completed.getOutcome() == null) {
                return commandStatePayload(completed, false);
            }
            return decorated;
        });
    }

    private CompletableFuture<JsonNode> sendRemoteStartCommand(
            String chargePointId,
            String idTag,
            Integer connectorId,
            Integer remoteStartId,
            String messageId
    ) {
        boolean ocpp201 = messageId == null ? isOcpp201(chargePointId) : remoteStartId != null;
        if (ocpp201) {
            ObjectNode idToken = objectMapper.createObjectNode();
            idToken.put("idToken", idTag);
            idToken.put("type", "Central");

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("remoteStartId", remoteStartId == null ? randomRemoteStartId() : remoteStartId);
            payload.set("idToken", idToken);
            if (connectorId != null) {
                payload.put("evseId", connectorId);
            }
            return messageId == null
                    ? sendCommand(chargePointId, "RequestStartTransaction", payload)
                    : sendCommand(chargePointId, "RequestStartTransaction", payload, messageId);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("idTag", idTag);
        if (connectorId != null) {
            payload.put("connectorId", connectorId);
        }
        return messageId == null
                ? sendCommand(chargePointId, "RemoteStartTransaction", payload)
                : sendCommand(chargePointId, "RemoteStartTransaction", payload, messageId);
    }

    private boolean acceptedRemoteStart(JsonNode response) {
        if (response == null || response.isNull()) {
            return false;
        }
        String status = response.path("status").asText("");
        return "Accepted".equalsIgnoreCase(status) || "Started".equalsIgnoreCase(status);
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
        return isOcpp201Protocol(connectionManager.getProtocol(chargePointId));
    }

    private boolean isOcpp201Protocol(String protocol) {
        return "OCPP201".equalsIgnoreCase(protocol);
    }

    static int deterministicRemoteStartId(String commandKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(commandKey.getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xff) << 24)
                    | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8)
                    | (digest[3] & 0xff);
            value &= Integer.MAX_VALUE;
            return value == 0 ? 1 : value;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private int randomRemoteStartId() {
        int value = UUID.randomUUID().hashCode() & Integer.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private String normalizeCommandKey(String commandKey) {
        if (commandKey == null) {
            return null;
        }
        if (commandKey.isBlank()) {
            throw new OcppProtocolException(
                    "commandKey must be 1-200 characters using letters, digits, '.', '_', ':', or '-'");
        }
        String normalized = commandKey.trim();
        if (!COMMAND_KEY_PATTERN.matcher(normalized).matches()) {
            throw new OcppProtocolException(
                    "commandKey must be 1-200 characters using letters, digits, '.', '_', ':', or '-'"
            );
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new OcppProtocolException("correlationId must not exceed 200 characters");
        }
        return normalized;
    }

    private JsonNode decorateResponse(
            JsonNode response,
            OcppRemoteStartCommand command,
            RemoteStartCommandOutcome outcome,
            boolean replayed
    ) {
        ObjectNode decorated;
        if (response != null && response.isObject()) {
            decorated = ((ObjectNode) response).deepCopy();
        } else {
            decorated = objectMapper.createObjectNode();
            if (response != null && !response.isNull()) {
                decorated.set("payload", response);
            }
        }
        if (!decorated.hasNonNull("status")) {
            decorated.put("status", responseStatus(RemoteStartCommandState.TERMINAL, outcome));
        }
        decorated.put("commandKey", command.getCommandKey());
        decorated.put("commandState", RemoteStartCommandState.TERMINAL.name());
        decorated.put("commandOutcome", outcome.name());
        decorated.put("replayed", replayed);
        if (command.getRemoteStartId() != null) {
            decorated.put("remoteStartId", command.getRemoteStartId());
        }
        return decorated;
    }

    private JsonNode replayPayload(OcppRemoteStartCommand command) {
        return commandStatePayload(command, true);
    }

    private JsonNode commandStatePayload(OcppRemoteStartCommand command, boolean replayed) {
        ObjectNode payload = storedObjectPayload(command.getResponsePayload());
        payload.put("status", responseStatus(command.getState(), command.getOutcome()));
        payload.put("commandKey", command.getCommandKey());
        payload.put("commandState", command.getState().name());
        if (command.getOutcome() == null) {
            payload.putNull("commandOutcome");
        } else {
            payload.put("commandOutcome", command.getOutcome().name());
        }
        payload.put("replayed", replayed);
        if (command.getRemoteStartId() != null) {
            payload.put("remoteStartId", command.getRemoteStartId());
        }
        if (command.getFailureReason() != null) {
            payload.put("failureReason", command.getFailureReason());
        }
        return payload;
    }

    private ObjectNode storedObjectPayload(String storedPayload) {
        if (storedPayload == null || storedPayload.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(storedPayload);
            if (parsed != null && parsed.isObject()) {
                return ((ObjectNode) parsed).deepCopy();
            }
        } catch (IOException exception) {
            log.warn("Unable to parse stored remote-start response payload", exception);
        }
        return objectMapper.createObjectNode();
    }

    private String responseStatus(
            RemoteStartCommandState state,
            RemoteStartCommandOutcome outcome
    ) {
        if (state == RemoteStartCommandState.PENDING) {
            return "Pending";
        }
        if (state == RemoteStartCommandState.UNKNOWN) {
            return "Unknown";
        }
        if (outcome == RemoteStartCommandOutcome.ACCEPTED) {
            return "Accepted";
        }
        if (outcome == RemoteStartCommandOutcome.REJECTED) {
            return "Rejected";
        }
        return "Failed";
    }

    private RemoteStartCommandStatusResponse statusResponse(OcppRemoteStartCommand command) {
        return new RemoteStartCommandStatusResponse(
                command.getCommandKey(),
                command.getState().name(),
                command.getOutcome() == null ? null : command.getOutcome().name(),
                command.getChargePointId(),
                command.getProtocol(),
                command.getRemoteStartId(),
                storedResponsePayload(command.getResponsePayload()),
                command.getFailureReason(),
                command.getCreatedAt(),
                command.getUpdatedAt()
        );
    }

    private Object storedResponsePayload(String storedPayload) {
        if (storedPayload == null || storedPayload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.convertValue(objectMapper.readTree(storedPayload), Object.class);
        } catch (IOException exception) {
            log.warn("Unable to parse stored remote-start response payload", exception);
            return null;
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static final class OcppCallErrorException extends RuntimeException {
        private OcppCallErrorException(String message) {
            super(message);
        }
    }

    private String commandOutcome(Throwable throwable) {
        if (throwable == null) {
            return "success";
        }
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        return "failure";
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

    public CompletableFuture<JsonNode> changeAvailability(String chargePointId, Integer connectorId, String type) {
        if (isOcpp201(chargePointId)) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("operationalStatus", normalizeOperationalStatus(type));
            if (connectorId != null) {
                ObjectNode evse = objectMapper.createObjectNode();
                evse.put("id", connectorId);
                payload.set("evse", evse);
            }
            return sendCommand(chargePointId, "ChangeAvailability", payload);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("connectorId", connectorId == null ? 0 : connectorId);
        payload.put("type", normalizeAvailabilityType(type));
        return sendCommand(chargePointId, "ChangeAvailability", payload);
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

    private String normalizeAvailabilityType(String type) {
        return "INOPERATIVE".equalsIgnoreCase(type) ? "Inoperative" : "Operative";
    }

    private String normalizeOperationalStatus(String type) {
        return "INOPERATIVE".equalsIgnoreCase(type) ? "Inoperative" : "Operative";
    }

}
