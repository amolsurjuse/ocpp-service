package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataTransferHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataTransferHandler.class);


    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Retrieves get action for `DataTransferHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        LOGGER.info("CODEx_ENTRY_LOG: Entering DataTransferHandler#getAction");
        LOGGER.debug("CODEx_ENTRY_LOG: Entering DataTransferHandler#getAction with debug context");
        return "DataTransfer";
    }

    /**
     * Processes handle for `DataTransferHandler`.
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
            String vendorId = payload.path("vendorId").asText();
            String messageId = payload.path("messageId").asText();
            JsonNode data = payload.path("data");

            log.info("Data transfer from {}: vendorId={}, messageId={}", chargePointId, vendorId, messageId);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Accepted");

            log.debug("DataTransfer response: status=Accepted");
            return response;
        } catch (Exception e) {
            log.error("Error handling DataTransfer: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Rejected");
            return response;
        }
    }

}
