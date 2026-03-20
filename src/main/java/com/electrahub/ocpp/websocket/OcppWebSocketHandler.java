package com.electrahub.ocpp.websocket;

import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.service.OcppMessageLogService;
import com.electrahub.ocpp.service.OcppMessageRouter;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
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

        connectionRepository.save(connection);
        log.debug("Saved OCPP connection to database: {}", chargePointId);
    }

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
                session.sendMessage(new TextMessage(responseJson));
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
            session.sendMessage(new TextMessage(errorResponse.toJson()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String chargePointId = extractChargePointId(session);
        log.info("WebSocket connection closed for charge point: {} with status: {}", chargePointId, status);

        connectionManager.removeConnection(chargePointId);

        connectionRepository.findByChargePointId(chargePointId).ifPresent(connection -> {
            connection.setDisconnectedAt(Instant.now());
            connection.setActive(false);
            connectionRepository.save(connection);
            log.debug("Updated OCPP connection in database: {}", chargePointId);
        });
    }

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

    private String extractChargePointId(WebSocketSession session) {
        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return "unknown";
    }

}
