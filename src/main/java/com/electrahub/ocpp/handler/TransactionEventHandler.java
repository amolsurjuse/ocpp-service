package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Slf4j
public class TransactionEventHandler implements OcppMessageHandler {
    private final SessionServiceClient sessionServiceClient;
    private final OcppTelemetryDispatcher telemetryDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes transaction event handler for `TransactionEventHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by TransactionEventHandler.
     */
    public TransactionEventHandler(
            SessionServiceClient sessionServiceClient,
            OcppTelemetryDispatcher telemetryDispatcher
    ) {
        this.sessionServiceClient = sessionServiceClient;
        this.telemetryDispatcher = telemetryDispatcher;
    }

    /**
     * Retrieves get action for `TransactionEventHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "TransactionEvent";
    }

    /**
     * Processes handle for `TransactionEventHandler`.
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
            String eventType = payload.path("eventType").asText();
            String timestamp = payload.path("timestamp").asText();
            String triggerReason = payload.path("triggerReason").asText();
            int connectorId = resolveConnectorId(payload);

        if ("Started".equalsIgnoreCase(eventType) || "Ended".equalsIgnoreCase(eventType)) {
            log.info("Transaction lifecycle event from {}: eventType={}, trigger={}", chargePointId, eventType, triggerReason);
        } else {
            log.debug("Transaction event from {}: eventType={}, trigger={}", chargePointId, eventType, triggerReason);
        }

            JsonNode transactionInfo = payload.path("transactionInfo");
            int transactionId = transactionInfo.path("transactionId").asInt(generateTransactionId());

            switch (eventType) {
                case "Started" -> {
                    String idTag = payload.path("idToken").path("idToken").asText(
                            payload.path("idTag").asText("")
                    );
                    String idTokenType = payload.path("idToken").path("type").asText(null);
                    int meterStart = integerValueOrZero(extractSnapshot(payload).energyWh());
                    sessionServiceClient.onStartTransaction(
                            chargePointId,
                            connectorId,
                            idTag,
                            idTokenType,
                            meterStart,
                            timestamp,
                            transactionId
                    );
                }
                case "Updated" -> {
                    String chargingState = transactionInfo.path("chargingState").asText("");
                    if ("ChargingStateChanged".equalsIgnoreCase(triggerReason) && !chargingState.isBlank()) {
                        JsonNode customData = payload.path("customData");
                        String errorCode = customData.path("errorCode").asText("NoError");
                        boolean endSessionRequested = customData.path("endSessionRequested").asBoolean(false);
                        telemetryDispatcher.dispatchStatusNotification(chargePointId, connectorId, () ->
                                sessionServiceClient.onStatusNotification(
                                        chargePointId,
                                        connectorId,
                                        chargingState,
                                        errorCode,
                                        timestamp,
                                        transactionId,
                                        endSessionRequested
                                )
                        );
                    } else {
                        MeterSnapshot snapshot = extractSnapshot(payload);
                        telemetryDispatcher.dispatchMeterValues(chargePointId, connectorId, () ->
                                sessionServiceClient.onMeterValues(
                                        chargePointId,
                                        transactionId,
                                        connectorId,
                                        timestamp,
                                        snapshot.energyWh(),
                                        snapshot.powerW(),
                                        snapshot.stateOfChargePercent()
                                )
                        );
                    }
                }
                case "Ended" -> {
                    MeterSnapshot snapshot = extractSnapshot(payload);
                    sessionServiceClient.onStopTransaction(
                            transactionId,
                            chargePointId,
                            connectorId,
                            integerValueOrZero(snapshot.energyWh()),
                            timestamp,
                            resolveStoppedReason(payload, triggerReason)
                    );
                }
            }

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("TransactionEvent response sent for eventType: {}", eventType);
            return response;
        } catch (Exception e) {
            if (SessionServiceClient.isExpectedSessionCallbackFailure(e)) {
                log.warn("TransactionEvent callback rejected by session-service for chargePointId={} summary={}",
                        chargePointId, SessionServiceClient.callbackFailureSummary(e));
            } else {
                log.error("Error handling TransactionEvent: {}", e.getMessage(), e);
            }
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

    private int resolveConnectorId(JsonNode payload) {
        int connectorId = payload.path("connectorId").asInt(0);
        if (connectorId > 0) {
            return connectorId;
        }
        return payload.path("evse").path("connectorId").asInt(
                payload.path("evse").path("id").asInt(1)
        );
    }

    private MeterSnapshot extractSnapshot(JsonNode payload) {
        BigDecimal energyWh = null;
        BigDecimal powerW = null;
        BigDecimal stateOfChargePercent = null;
        JsonNode meterValues = payload.path("meterValue");

        if (meterValues.isArray() && !meterValues.isEmpty()) {
            for (JsonNode meterValue : meterValues) {
                JsonNode sampledValues = meterValue.path("sampledValue");
                if (!sampledValues.isArray()) {
                    continue;
                }
                for (JsonNode sampledValue : sampledValues) {
                    String value = sampledValue.path("value").asText(null);
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    BigDecimal numeric;
                    try {
                        numeric = new BigDecimal(value);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }

                    String measurand = sampledValue.path("measurand").asText("");
                    if (measurand.equalsIgnoreCase("Energy.Active.Import.Register")) {
                        energyWh = numeric;
                    } else if (measurand.equalsIgnoreCase("Power.Active.Import")) {
                        powerW = numeric;
                    } else if (measurand.equalsIgnoreCase("SoC")) {
                        stateOfChargePercent = numeric;
                    }
                }
            }
        }
        return new MeterSnapshot(energyWh, powerW, stateOfChargePercent);
    }

    private int integerValueOrZero(BigDecimal value) {
        return value == null ? 0 : value.intValue();
    }

    private String resolveStoppedReason(JsonNode payload, String triggerReason) {
        String stoppedReason = payload.path("transactionInfo").path("stoppedReason").asText(null);
        if (stoppedReason != null && !stoppedReason.isBlank()) {
            return stoppedReason;
        }
        return triggerReason;
    }

    private int generateTransactionId() {
        long nowMillis = Instant.now().toEpochMilli();
        return (int) (nowMillis % Integer.MAX_VALUE);
    }

    private record MeterSnapshot(BigDecimal energyWh, BigDecimal powerW, BigDecimal stateOfChargePercent) {
    }

}
