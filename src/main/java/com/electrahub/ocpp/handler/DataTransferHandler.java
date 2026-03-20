package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataTransferHandler implements OcppMessageHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getAction() {
        return "DataTransfer";
    }

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
