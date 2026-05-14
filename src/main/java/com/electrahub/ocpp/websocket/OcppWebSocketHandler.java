package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
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
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class OcppWebSocketHandler extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppWebSocketHandler.class);


    private final ConnectionManager connectionManager;
    private final OcppMessageRouter messageRouter;
    private final OcppConnectionRepository connectionRepository;
    private final OcppMessageLogService messageLogService;

    public OcppWebSocketHandler(
            ConnectionManager connectionManager,
            OcppMessageRouter messageRouter,
            OcppConnectionRepository connectionRepository,
            OcppMessageLogService messageLogService) {
        this.connectionManager = connectionManager;
        this.messageRouter = messageRouter;
        this.connectionRepository = connectionRepository;
        this.messageLogService = messageLogService;
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

        OcppConnection connection = OcppConnection.builder()
            .id(UUID.randomUUID())
            .chargePointId(chargePointId)
            .nodeId(connectionManager.getNodeId())
            .connectedAt(Instant.now())
            .active(true)
            .build();

        try {
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

        log.debug("Received message from {}: {}", chargePointId, payload);

        try {
            OcppJsonRpcMessage ocppMessage = OcppJsonRpcMessage.parse(payload);
            OcppJsonRpcMessage response = messageRouter.routeMessage(chargePointId, ocppMessage);

            if (response != null) {
                String responseJson = response.toJson();
                connectionManager.sendMessage(session, responseJson);
                log.debug("Sent response to {}: {}", chargePointId, responseJson);
            }
        } catch (Exception e) {
            log.error("Error handling message from {}: {}", chargePointId, e.getMessage(), e);
            OcppJsonRpcMessage errorResponse = OcppJsonRpcMessage.createCallError(
                UUID.randomUUID().toString(),
                "INTERNAL_ERROR",
                "Failed to process message",
                null
            );
            connectionManager.sendMessage(session, errorResponse.toJson());
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

        connectionManager.removeConnection(chargePointId);

        try {
            connectionRepository.findByChargePointId(chargePointId).ifPresent(connection -> {
                connection.setDisconnectedAt(Instant.now());
                connection.setActive(false);
                connectionRepository.save(connection);
                log.debug("Updated OCPP connection in database: {}", chargePointId);
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to persist OCPP disconnect audit row for {}: {}", chargePointId, ex.getMessage());
        }
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

}
