package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.domain.enums.OcppMessageType;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.service.ChargePointAvailabilityService;
import com.electrahub.ocpp.service.OcppMessageLogService;
import com.electrahub.ocpp.service.OcppMessageRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OcppWebSocketHandler extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppWebSocketHandler.class);
    private static final Duration ACTIVITY_PERSIST_INTERVAL = Duration.ofSeconds(15);


    private final ConnectionManager connectionManager;
    private final OcppMessageRouter messageRouter;
    private final OcppConnectionRepository connectionRepository;
    private final OcppMessageLogService messageLogService;
    private final ChargePointAvailabilityService availabilityService;
    private final OcppInboundCallDispatcher inboundCallDispatcher;
    private final Map<String, Instant> lastActivityPersistedAt = new ConcurrentHashMap<>();

    public OcppWebSocketHandler(
            ConnectionManager connectionManager,
            OcppMessageRouter messageRouter,
            OcppConnectionRepository connectionRepository,
            OcppMessageLogService messageLogService,
            ChargePointAvailabilityService availabilityService,
            OcppInboundCallDispatcher inboundCallDispatcher) {
        this.connectionManager = connectionManager;
        this.messageRouter = messageRouter;
        this.connectionRepository = connectionRepository;
        this.messageLogService = messageLogService;
        this.availabilityService = availabilityService;
        this.inboundCallDispatcher = inboundCallDispatcher;
    }

    /**
     * Executes after connection established for `OcppWebSocketHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param session input consumed by afterConnectionEstablished.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        LOGGER.info(" Entering OcppWebSocketHandler#afterConnectionEstablished");
        LOGGER.debug(" Entering OcppWebSocketHandler#afterConnectionEstablished with debug context");
        String chargePointId = extractChargePointId(session);
        log.info("WebSocket connection established for charge point: {}", chargePointId);

        connectionManager.registerConnection(chargePointId, session);

        try {
            OcppConnection connection = connectionRepository.findByChargePointId(chargePointId)
                .orElseGet(() -> OcppConnection.builder()
                    .chargePointId(chargePointId)
                    .build());
            connection.setNodeId(connectionManager.getNodeId());
            connection.setConnectedAt(Instant.now());
            connection.setLastHeartbeatAt(Instant.now());
            connection.setLastSeenAt(Instant.now());
            connection.setDisconnectedAt(null);
            connection.setActive(true);
            connectionRepository.save(connection);
            log.debug("Saved OCPP connection to database: {}", chargePointId);
        } catch (DataAccessException ex) {
            log.warn("Unable to persist OCPP connection audit row for {}: {}", chargePointId, ex.getMessage());
        }
    }

    /**
     * Processes handle text message for `OcppWebSocketHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param session input consumed by handleTextMessage.
     * @param message input consumed by handleTextMessage.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String chargePointId = extractChargePointId(session);
        String payload = message.getPayload();

        log.debug("Received OCPP message from {}: bytes={}", chargePointId, payload.length());

        try {
            OcppJsonRpcMessage ocppMessage = OcppJsonRpcMessage.parse(payload);
            if (isCommandResponse(ocppMessage)) {
                // Remote-command callers are waiting for this response. Do not put it
                // behind REST callbacks such as MeterValues or StatusNotification.
                messageRouter.routeMessage(chargePointId, ocppMessage);
                return;
            }

            if (ocppMessage.getMessageTypeId() != OcppMessageType.CALL.getValue()) {
                messageRouter.routeMessage(chargePointId, ocppMessage);
                return;
            }

            boolean accepted = inboundCallDispatcher.dispatch(
                    chargePointId,
                    () -> processInboundCall(chargePointId, session, ocppMessage),
                    () -> sendCallError(session, ocppMessage.getMessageId(), "InternalError",
                            "CSMS callback processing is unavailable")
            );
            if (!accepted) {
                sendCallError(session, ocppMessage.getMessageId(), "InternalError",
                        "CSMS callback queue is full");
            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", chargePointId, e.getMessage(), e);
            sendCallError(session, UUID.randomUUID().toString(), "InternalError", "Failed to process message");
        }
    }

    private void processInboundCall(
            String chargePointId,
            WebSocketSession session,
            OcppJsonRpcMessage ocppMessage
    ) {
        try {
            touchConnectionActivity(chargePointId, session);
            updateProtocolFromMessage(chargePointId, ocppMessage);
            OcppJsonRpcMessage response = messageRouter.routeMessage(chargePointId, ocppMessage);
            if (response != null) {
                String responseJson = response.toJson();
                connectionManager.sendMessage(session, responseJson);
                log.debug("Sent OCPP response to {}: bytes={}", chargePointId, responseJson.length());
            }
        } catch (Exception e) {
            log.error("Error processing inbound {} callback from {}: {}",
                    ocppMessage.getAction(), chargePointId, e.getMessage(), e);
            sendCallError(session, ocppMessage.getMessageId(), "InternalError", "Failed to process message");
        }
    }

    private boolean isCommandResponse(OcppJsonRpcMessage message) {
        return message.getMessageTypeId() == OcppMessageType.CALL_RESULT.getValue()
                || message.getMessageTypeId() == OcppMessageType.CALL_ERROR.getValue();
    }

    private void sendCallError(
            WebSocketSession session,
            String messageId,
            String errorCode,
            String errorDescription
    ) {
        try {
            connectionManager.sendMessage(session, OcppJsonRpcMessage.createCallError(
                    messageId,
                    errorCode,
                    errorDescription,
                    null
            ).toJson());
        } catch (IOException ioException) {
            log.warn("Unable to send OCPP CallError to {}: {}", session.getId(), ioException.getMessage());
        }
    }

    /**
     * Executes after connection closed for `OcppWebSocketHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param session input consumed by afterConnectionClosed.
     * @param status input consumed by afterConnectionClosed.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String chargePointId = extractChargePointId(session);
        log.info("WebSocket connection closed for charge point: {} with status: {}", chargePointId, status);

        if (connectionManager.removeConnection(chargePointId, session)) {
            availabilityService.markOffline(chargePointId, "OCPP_WEBSOCKET_DISCONNECTED", false);
        }
        lastActivityPersistedAt.remove(chargePointId);
    }

    /**
     * Processes handle transport error for `OcppWebSocketHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param session input consumed by handleTransportError.
     * @param exception input consumed by handleTransportError.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String chargePointId = extractChargePointId(session);
        log.error("Transport error for charge point {}: {}", chargePointId, exception.getMessage(), exception);

        try {
            session.close();
        } catch (IOException e) {
            log.error("Error closing session after transport error: {}", e.getMessage());
        }
    }

    /**
     * Executes extract charge point id for `OcppWebSocketHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param session input consumed by extractChargePointId.
     * @return result produced by extractChargePointId.
     */
    private String extractChargePointId(WebSocketSession session) {
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return "unknown";
    }

    private void updateProtocolFromMessage(String chargePointId, OcppJsonRpcMessage message) {
        if (!"BootNotification".equals(message.getAction()) || message.getPayload() == null) {
            return;
        }
        String protocol = message.getPayload().has("chargingStation") ? "OCPP201" : "OCPP16J";
        connectionManager.setProtocol(chargePointId, protocol);
        try {
            connectionRepository.findByChargePointIdAndActiveTrue(chargePointId).ifPresent(connection -> {
                connection.setOcppProtocol(protocol);
                connectionRepository.save(connection);
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to persist OCPP protocol {} for {}: {}", protocol, chargePointId, ex.getMessage());
        }
    }

    private void touchConnectionActivity(String chargePointId, WebSocketSession session) {
        boolean routeMissing = !connectionManager.isConnected(chargePointId);
        if (routeMissing) {
            connectionManager.registerConnection(chargePointId, session);
        }
        Instant now = Instant.now();
        if (!routeMissing && !shouldPersistActivity(chargePointId, now)) {
            return;
        }
        try {
            connectionRepository.findByChargePointId(chargePointId).ifPresent(connection -> {
                connection.setNodeId(connectionManager.getNodeId());
                connection.setLastSeenAt(now);
                connection.setDisconnectedAt(null);
                connection.setActive(true);
                connectionRepository.save(connection);
                lastActivityPersistedAt.put(chargePointId, now);
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to persist OCPP connection activity for {}: {}", chargePointId, ex.getMessage());
        }
    }

    private boolean shouldPersistActivity(String chargePointId, Instant now) {
        Instant previous = lastActivityPersistedAt.get(chargePointId);
        return previous == null || previous.plus(ACTIVITY_PERSIST_INTERVAL).isBefore(now);
    }

}
