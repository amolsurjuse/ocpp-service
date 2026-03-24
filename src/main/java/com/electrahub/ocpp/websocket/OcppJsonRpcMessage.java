package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.enums.OcppMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcppJsonRpcMessage {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppJsonRpcMessage.class);


    private int messageTypeId;
    private String messageId;
    private String action;
    private JsonNode payload;
    private String errorCode;
    private String errorDescription;
    private JsonNode errorDetails;

    /**
     * Creates create call for `OcppJsonRpcMessage`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param messageId input consumed by createCall.
     * @param action input consumed by createCall.
     * @param payload input consumed by createCall.
     * @return result produced by createCall.
     */
    public static OcppJsonRpcMessage createCall(String messageId, String action, JsonNode payload) {
        LOGGER.info("CODEx_ENTRY_LOG: Entering OcppJsonRpcMessage#createCall");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering OcppJsonRpcMessage#createCall with debug context");
        OcppJsonRpcMessage msg = new OcppJsonRpcMessage();
        msg.setMessageTypeId(OcppMessageType.CALL.getValue());
        msg.setMessageId(messageId);
        msg.setAction(action);
        msg.setPayload(payload);
        return msg;
    }

    /**
     * Creates create call result for `OcppJsonRpcMessage`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param messageId input consumed by createCallResult.
     * @param payload input consumed by createCallResult.
     * @return result produced by createCallResult.
     */
    public static OcppJsonRpcMessage createCallResult(String messageId, JsonNode payload) {
        OcppJsonRpcMessage msg = new OcppJsonRpcMessage();
        msg.setMessageTypeId(OcppMessageType.CALL_RESULT.getValue());
        msg.setMessageId(messageId);
        msg.setPayload(payload);
        return msg;
    }

    /**
     * Creates create call error for `OcppJsonRpcMessage`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param messageId input consumed by createCallError.
     * @param errorCode input consumed by createCallError.
     * @param errorDescription input consumed by createCallError.
     * @param errorDetails input consumed by createCallError.
     * @return result produced by createCallError.
     */
    public static OcppJsonRpcMessage createCallError(String messageId, String errorCode, String errorDescription, JsonNode errorDetails) {
        OcppJsonRpcMessage msg = new OcppJsonRpcMessage();
        msg.setMessageTypeId(OcppMessageType.CALL_ERROR.getValue());
        msg.setMessageId(messageId);
        msg.setErrorCode(errorCode);
        msg.setErrorDescription(errorDescription);
        msg.setErrorDetails(errorDetails);
        return msg;
    }

    /**
     * Executes to json for `OcppJsonRpcMessage`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @return result produced by toJson.
     */
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        arrayNode.add(messageTypeId);
        arrayNode.add(messageId);

        if (messageTypeId == OcppMessageType.CALL.getValue()) {
            arrayNode.add(action);
            arrayNode.add(payload != null ? payload : mapper.createObjectNode());
        } else if (messageTypeId == OcppMessageType.CALL_RESULT.getValue()) {
            arrayNode.add(payload != null ? payload : mapper.createObjectNode());
        } else if (messageTypeId == OcppMessageType.CALL_ERROR.getValue()) {
            arrayNode.add(errorCode);
            arrayNode.add(errorDescription);
            arrayNode.add(errorDetails != null ? errorDetails : mapper.createObjectNode());
        }

        return arrayNode.toString();
    }

    /**
     * Executes parse for `OcppJsonRpcMessage`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param json input consumed by parse.
     * @return result produced by parse.
     */
    public static OcppJsonRpcMessage parse(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(json);
            if (!node.isArray()) {
                throw new IllegalArgumentException("OCPP message must be an array");
            }

            int messageTypeId = node.get(0).asInt();
            OcppMessageType type = OcppMessageType.fromValue(messageTypeId);

            OcppJsonRpcMessage msg = new OcppJsonRpcMessage();
            msg.setMessageTypeId(messageTypeId);
            msg.setMessageId(node.get(1).asText());

            if (type == OcppMessageType.CALL) {
                msg.setAction(node.get(2).asText());
                msg.setPayload(node.get(3));
            } else if (type == OcppMessageType.CALL_RESULT) {
                msg.setPayload(node.get(2));
            } else if (type == OcppMessageType.CALL_ERROR) {
                msg.setErrorCode(node.get(2).asText());
                msg.setErrorDescription(node.get(3).asText());
                if (node.size() > 4) {
                    msg.setErrorDetails(node.get(4));
                }
            }

            return msg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OCPP message", e);
        }
    }

}
