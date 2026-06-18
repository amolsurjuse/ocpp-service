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
public class MeterValuesHandler implements OcppMessageHandler {
    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes meter values handler for `MeterValuesHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by MeterValuesHandler.
     */
    public MeterValuesHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    /**
     * Retrieves get action for `MeterValuesHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "MeterValues";
    }

    /**
     * Processes handle for `MeterValuesHandler`.
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
            int connectorId = payload.path("connectorId").asInt();
            int transactionId = payload.path("transactionId").asInt();
            String timestamp = extractTimestamp(payload);
            MeterSnapshot snapshot = extractSnapshot(payload);

            log.info("Processing meter values for transaction: {}", transactionId);

            sessionServiceClient.onMeterValues(
                    chargePointId,
                    transactionId,
                    connectorId,
                    timestamp,
                    snapshot.energyWh(),
                    snapshot.powerW()
            );

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("MeterValues response sent for transaction: {}", transactionId);
            return response;
        } catch (Exception e) {
            log.error("Error handling MeterValues: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

    private String extractTimestamp(JsonNode payload) {
        JsonNode topLevel = payload.path("timestamp");
        if (!topLevel.isMissingNode() && !topLevel.isNull() && !topLevel.asText().isBlank()) {
            return topLevel.asText();
        }

        JsonNode meterValues = payload.path("meterValue");
        if (meterValues.isArray() && !meterValues.isEmpty()) {
            JsonNode first = meterValues.get(0).path("timestamp");
            if (!first.isMissingNode() && !first.isNull() && !first.asText().isBlank()) {
                return first.asText();
            }
        }
        return null;
    }

    private MeterSnapshot extractSnapshot(JsonNode payload) {
        BigDecimal energyWh = null;
        BigDecimal powerW = null;
        JsonNode meterValues = payload.path("meterValue");

        if (meterValues.isArray() && !meterValues.isEmpty()) {
            for (JsonNode meterValue : meterValues) {
                JsonNode sampledValues = meterValue.path("sampledValue");
                if (!sampledValues.isArray()) {
                    continue;
                }

                for (JsonNode sampledValue : sampledValues) {
                    String rawValue = sampledValue.path("value").asText(null);
                    if (rawValue == null || rawValue.isBlank()) {
                        continue;
                    }

                    BigDecimal numericValue;
                    try {
                        numericValue = new BigDecimal(rawValue);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }

                    String measurand = sampledValue.path("measurand").asText("");
                    if (energyWh == null && measurand.equalsIgnoreCase("Energy.Active.Import.Register")) {
                        energyWh = numericValue;
                    } else if (powerW == null && measurand.equalsIgnoreCase("Power.Active.Import")) {
                        powerW = numericValue;
                    }
                }
            }
        }

        if (energyWh == null) {
            energyWh = BigDecimal.ZERO;
        }
        if (powerW == null) {
            powerW = BigDecimal.ZERO;
        }

        return new MeterSnapshot(energyWh, powerW);
    }

    private record MeterSnapshot(BigDecimal energyWh, BigDecimal powerW) {
    }

}
