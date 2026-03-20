package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.enums.OcppMessageType;
import com.electrahub.ocpp.websocket.OcppJsonRpcMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class OcppMessageRouter {

    private final Map<String, OcppMessageHandler> handlers = new HashMap<>();
    private final RemoteCommandService remoteCommandService;
    private final Map<String, CompletableFuture<com.fasterxml.jackson.databind.JsonNode>> pendingResponses = new java.util.concurrent.ConcurrentHashMap<>();

    public OcppMessageRouter(
            List<OcppMessageHandler> handlers,
            RemoteCommandService remoteCommandService) {
        this.remoteCommandService = remoteCommandService;
        for (OcppMessageHandler handler : handlers) {
            this.handlers.put(handler.getAction(), handler);
        }
    }

    public OcppJsonRpcMessage routeMessage(String chargePointId, OcppJsonRpcMessage message) {
        OcppMessageType type = OcppMessageType.fromValue(message.getMessageTypeId());

        if (type == OcppMessageType.CALL) {
            return handleCall(chargePointId, message);
        } else if (type == OcppMessageType.CALL_RESULT) {
            handleCallResult(message);
            return null;
        } else if (type == OcppMessageType.CALL_ERROR) {
            handleCallError(message);
            return null;
        }

        return null;
    }

    private OcppJsonRpcMessage handleCall(String chargePointId, OcppJsonRpcMessage message) {
        String action = message.getAction();
        log.info("Handling CALL from {}: action={}, messageId={}", chargePointId, action, message.getMessageId());

        OcppMessageHandler handler = handlers.get(action);
        if (handler == null) {
            log.warn("No handler found for action: {}", action);
            return OcppJsonRpcMessage.createCallError(
                message.getMessageId(),
                "NOT_IMPLEMENTED",
                "Handler not found for action: " + action,
                null
            );
        }

        try {
            com.fasterxml.jackson.databind.JsonNode response = handler.handle(chargePointId, message.getPayload());
            return OcppJsonRpcMessage.createCallResult(message.getMessageId(), response);
        } catch (Exception e) {
            log.error("Error handling action {}: {}", action, e.getMessage(), e);
            return OcppJsonRpcMessage.createCallError(
                message.getMessageId(),
                "INTERNAL_ERROR",
                "Error processing action: " + e.getMessage(),
                null
            );
        }
    }

    private void handleCallResult(OcppJsonRpcMessage message) {
        log.debug("Handling CALL_RESULT: messageId={}", message.getMessageId());
        remoteCommandService.resolvePendingResponse(message.getMessageId(), message.getPayload());
    }

    private void handleCallError(OcppJsonRpcMessage message) {
        log.warn("Handling CALL_ERROR: messageId={}, errorCode={}, description={}",
            message.getMessageId(), message.getErrorCode(), message.getErrorDescription());
        remoteCommandService.rejectPendingResponse(
            message.getMessageId(),
            message.getErrorCode(),
            message.getErrorDescription()
        );
    }

}
