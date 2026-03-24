package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MeterValuesHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeterValuesHandler.class);


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
        LOGGER.info("CODEx_ENTRY_LOG: Entering MeterValuesHandler#MeterValuesHandler");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering MeterValuesHandler#MeterValuesHandler with debug context");
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

            log.info("Processing meter values for transaction: {}", transactionId);

            // Call session service to store meter values
            sessionServiceClient.addMeterValues(transactionId, payload);

            ObjectNode response = objectMapper.createObjectNode();
            log.debug("MeterValues response sent for transaction: {}", transactionId);
            return response;
        } catch (Exception e) {
            log.error("Error handling MeterValues: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            return response;
        }
    }

}
