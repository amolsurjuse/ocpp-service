package com.electrahub.ocpp.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppMessageRouter.class);


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

    /**
     * Executes route message for `OcppMessageRouter`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by routeMessage.
     * @param message input consumed by routeMessage.
     * @return result produced by routeMessage.
     */
    public OcppJsonRpcMessage routeMessage(String chargePointId, OcppJsonRpcMessage message) {
        LOGGER.debug("Entering OcppMessageRouter#routeMessage");
        LOGGER.debug(" Entering OcppMessageRouter#routeMessage with debug context");
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

    /**
     * Processes handle call for `OcppMessageRouter`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param chargePointId input consumed by handleCall.
     * @param message input consumed by handleCall.
     * @return result produced by handleCall.
     */
    private OcppJsonRpcMessage handleCall(String chargePointId, OcppJsonRpcMessage message) {
        String action = message.getAction();
        log.debug("Handling CALL from {}: action={}, messageId={}", chargePointId, action, message.getMessageId());

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
            com.fasterxml.jackson.databind.JsonNode response = handler.handle(
                    chargePointId, message.getMessageId(), message.getPayload());
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

    /**
     * Processes handle call result for `OcppMessageRouter`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param message input consumed by handleCallResult.
     */
    private void handleCallResult(OcppJsonRpcMessage message) {
        log.debug("Handling CALL_RESULT: messageId={}", message.getMessageId());
        remoteCommandService.resolvePendingResponse(message.getMessageId(), message.getPayload());
    }

    /**
     * Processes handle call error for `OcppMessageRouter`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     * @param message input consumed by handleCallError.
     */
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
