package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class StopTransactionHandler implements OcppMessageHandler {
    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes stop transaction handler for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by StopTransactionHandler.
     */
    public StopTransactionHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    /**
     * Retrieves get action for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "StopTransaction";
    }

    /**
     * Processes handle for `StopTransactionHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param chargePointId input consumed by handle.
     * @param payload input consumed by handle.
     * @return result produced by handle.
     */
    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            int transactionId = resolveTransactionId(payload);
            int meterStop = resolveMeterStop(payload);
            String timestamp = payload.path("timestamp").asText();
            String reason = resolveReason(payload);

            log.info("Stopping transaction: {}, meter: {}", transactionId, meterStop);

            if (transactionId <= 0) {
                log.warn("Ignoring StopTransaction without valid transactionId for chargePoint {}", chargePointId);
                ObjectNode response = objectMapper.createObjectNode();
                ObjectNode idTagInfo = objectMapper.createObjectNode();
                idTagInfo.put("status", "Accepted");
                response.set("idTagInfo", idTagInfo);
                return response;
            }

            sessionServiceClient.onStopTransaction(transactionId, meterStop, timestamp, reason);

            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.set("idTagInfo", idTagInfo);

            log.debug("StopTransaction response: transactionId={}", transactionId);
            return response;
        } catch (Exception e) {
            log.error("Error handling StopTransaction: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

    private int resolveTransactionId(JsonNode payload) {
        int transactionId = parseFlexibleInt(payload.path("transactionId"));
        if (transactionId > 0) {
            return transactionId;
        }
        return parseFlexibleInt(payload.path("transactionInfo").path("transactionId"));
    }

    private int resolveMeterStop(JsonNode payload) {
        int meterStop = parseFlexibleInt(payload.path("meterStop"));
        if (meterStop != Integer.MIN_VALUE) {
            return meterStop;
        }
        JsonNode meterValues = payload.path("meterValue");
        if (meterValues.isArray()) {
            for (JsonNode meterValue : meterValues) {
                JsonNode sampledValues = meterValue.path("sampledValue");
                if (!sampledValues.isArray()) {
                    continue;
                }
                for (JsonNode sampledValue : sampledValues) {
                    String measurand = sampledValue.path("measurand").asText("");
                    if ("Energy.Active.Import.Register".equalsIgnoreCase(measurand)) {
                        int parsed = parseFlexibleInt(sampledValue.path("value"));
                        if (parsed != Integer.MIN_VALUE) {
                            return parsed;
                        }
                    }
                }
            }
        }
        return 0;
    }

    private String resolveReason(JsonNode payload) {
        String reason = payload.path("reason").asText("");
        if (!reason.isBlank()) {
            return reason;
        }
        String stoppedReason = payload.path("stoppedReason").asText("");
        if (!stoppedReason.isBlank()) {
            return stoppedReason;
        }
        String txStoppedReason = payload.path("transactionInfo").path("stoppedReason").asText("");
        if (!txStoppedReason.isBlank()) {
            return txStoppedReason;
        }
        return "Local";
    }

    private int parseFlexibleInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Integer.MIN_VALUE;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isNumber()) {
            return node.decimalValue().intValue();
        }
        String raw = node.asText("");
        if (raw.isBlank()) {
            return Integer.MIN_VALUE;
        }
        try {
            return new BigDecimal(raw.trim()).intValue();
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

}
