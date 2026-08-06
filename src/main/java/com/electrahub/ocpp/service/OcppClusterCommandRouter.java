package com.electrahub.ocpp.service;

import com.electrahub.ocpp.exception.ChargePointNotConnectedException;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** Routes a command to the pod that owns the charge-point WebSocket. */
@Service
@Slf4j
public class OcppClusterCommandRouter implements MessageListener {

    private static final String COMMAND_CHANNEL_PREFIX = "ocpp:node:commands:";
    private static final String RESULT_CHANNEL_PREFIX = "ocpp:node:results:";

    private final ConnectionManager connections;
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RemoteCommandService> commandService;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final int timeoutSeconds;
    private final ConcurrentHashMap<UUID, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    public OcppClusterCommandRouter(
            ConnectionManager connections,
            RedisTemplate<String, String> redis,
            ObjectMapper objectMapper,
            ObjectProvider<RemoteCommandService> commandService,
            MeterRegistry meterRegistry,
            @Value("${app.ocpp.cluster-routing.enabled:false}") boolean enabled,
            @Value("${ocpp.message.response-timeout-seconds:30}") int timeoutSeconds
    ) {
        this.connections = connections;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.commandService = commandService;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String commandChannel() {
        return commandChannel(connections.getNodeId());
    }

    public String resultChannel() {
        return resultChannel(connections.getNodeId());
    }

    public CompletableFuture<JsonNode> route(
            String ownerNodeId,
            String chargePointId,
            String action,
            JsonNode payload
    ) {
        return route(ownerNodeId, chargePointId, action, payload, UUID.randomUUID().toString());
    }

    public CompletableFuture<JsonNode> route(
            String ownerNodeId,
            String chargePointId,
            String action,
            JsonNode payload,
            String messageId
    ) {
        if (!enabled) {
            return CompletableFuture.failedFuture(new IllegalStateException("OCPP cluster routing is disabled"));
        }

        UUID commandId = UUID.randomUUID();
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pending.put(commandId, response);
        response.orTimeout(timeoutSeconds + 1L, TimeUnit.SECONDS)
                .whenComplete((ignored, failure) -> pending.remove(commandId, response));

        RoutedCommand command = new RoutedCommand(
                commandId,
                connections.getNodeId(),
                chargePointId,
                action,
                payload,
                messageId,
                Instant.now()
        );
        try {
            Long subscribers = redis.convertAndSend(
                    commandChannel(ownerNodeId), objectMapper.writeValueAsString(command));
            if (subscribers == null || subscribers == 0L) {
                pending.remove(commandId, response);
                response.completeExceptionally(new ChargePointNotConnectedException(
                        "The owning OCPP node is not subscribed to cluster commands"));
                meterRegistry.counter(
                        "electrahub.ocpp.cluster_command", "outcome", "owner_unavailable").increment();
                return response;
            }
            meterRegistry.counter("electrahub.ocpp.cluster_command", "outcome", "routed").increment();
        } catch (DataAccessException | JsonProcessingException exception) {
            pending.remove(commandId, response);
            response.completeExceptionally(exception);
            meterRegistry.counter("electrahub.ocpp.cluster_command", "outcome", "publish_failure").increment();
        }
        return response;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if (channel.equals(commandChannel())) {
                handleCommand(objectMapper.readValue(body, RoutedCommand.class));
            } else if (channel.equals(resultChannel())) {
                handleResult(objectMapper.readValue(body, RoutedResult.class));
            }
        } catch (Exception exception) {
            log.error("Unable to process clustered OCPP message from channel {}", channel, exception);
            meterRegistry.counter("electrahub.ocpp.cluster_command", "outcome", "invalid_message").increment();
        }
    }

    private void handleCommand(RoutedCommand command) {
        if (!connections.isLocalConnectionOwner(command.chargePointId())) {
            publishResult(command.originNodeId(), RoutedResult.failure(
                    command.commandId(),
                    "CHARGE_POINT_NOT_CONNECTED",
                    "The owning pod no longer has the charge-point connection"
            ));
            return;
        }
        if (command.requestedAt().plusSeconds(timeoutSeconds + 1L).isBefore(Instant.now())) {
            publishResult(command.originNodeId(), RoutedResult.failure(
                    command.commandId(),
                    "COMMAND_EXPIRED",
                    "The routed command expired before delivery"
            ));
            return;
        }

        commandService.getObject()
                .sendCommandLocally(
                        command.chargePointId(), command.action(), command.payload(), command.messageId())
                .whenComplete((payload, failure) -> {
                    if (failure == null) {
                        publishResult(command.originNodeId(), RoutedResult.success(command.commandId(), payload));
                    } else {
                        Throwable cause = unwrap(failure);
                        publishResult(command.originNodeId(), RoutedResult.failure(
                                command.commandId(),
                                cause instanceof ChargePointNotConnectedException
                                        ? "CHARGE_POINT_NOT_CONNECTED" : "COMMAND_FAILED",
                                cause.getMessage()
                        ));
                    }
                });
    }

    private void handleResult(RoutedResult result) {
        CompletableFuture<JsonNode> response = pending.remove(result.commandId());
        if (response == null) {
            log.debug("Ignoring late or unknown clustered OCPP result {}", result.commandId());
            return;
        }
        if (result.success()) {
            response.complete(result.payload());
            meterRegistry.counter("electrahub.ocpp.cluster_command", "outcome", "success").increment();
        } else {
            response.completeExceptionally(new RuntimeException(result.errorCode() + ": " + result.errorMessage()));
            meterRegistry.counter("electrahub.ocpp.cluster_command", "outcome", "failure").increment();
        }
    }

    private void publishResult(String originNodeId, RoutedResult result) {
        try {
            Long subscribers = redis.convertAndSend(
                    resultChannel(originNodeId), objectMapper.writeValueAsString(result));
            if (subscribers == null || subscribers == 0L) {
                log.warn("Origin OCPP node {} is unavailable for command result {}",
                        originNodeId, result.commandId());
            }
        } catch (DataAccessException | JsonProcessingException exception) {
            log.error("Unable to publish clustered OCPP result {} to node {}",
                    result.commandId(), originNodeId, exception);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    private static String commandChannel(String nodeId) {
        return COMMAND_CHANNEL_PREFIX + nodeId;
    }

    private static String resultChannel(String nodeId) {
        return RESULT_CHANNEL_PREFIX + nodeId;
    }

    public record RoutedCommand(
            UUID commandId,
            String originNodeId,
            String chargePointId,
            String action,
            JsonNode payload,
            String messageId,
            Instant requestedAt
    ) {}

    public record RoutedResult(
            UUID commandId,
            boolean success,
            JsonNode payload,
            String errorCode,
            String errorMessage
    ) {
        static RoutedResult success(UUID commandId, JsonNode payload) {
            return new RoutedResult(commandId, true, payload, null, null);
        }

        static RoutedResult failure(UUID commandId, String errorCode, String errorMessage) {
            return new RoutedResult(commandId, false, null, errorCode,
                    errorMessage == null ? "Unknown command failure" : errorMessage);
        }
    }
}
