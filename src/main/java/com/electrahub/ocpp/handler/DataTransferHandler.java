package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.integration.SessionServiceClient;
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
    private final SessionServiceClient sessionServiceClient;

    public DataTransferHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    /**
     * Retrieves get action for `DataTransferHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        LOGGER.info(" Entering DataTransferHandler#getAction");
        LOGGER.debug(" Entering DataTransferHandler#getAction with debug context");
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

            log.info("Data transfer from {}: vendorId={}, messageId={}", chargePointId, vendorId, messageId);

            ObjectNode response = objectMapper.createObjectNode();
            if (!"org.openchargealliance.iso15118pnc".equals(vendorId)) {
                response.put("status", "UnknownVendorId");
                return response;
            }
            if (!"Authorize".equals(messageId)) {
                response.put("status", "UnknownMessageId");
                return response;
            }

            JsonNode data = parseData(payload.path("data"));
            String emaid = data.path("idToken").path("idToken").asText();
            String certificate = data.path("certificate").asText(null);
            SessionServiceClient.AuthorizationResult authorization = sessionServiceClient.authorize(emaid, "eMAID", certificate);

            ObjectNode idTokenInfo = objectMapper.createObjectNode();
            idTokenInfo.put("status", authorization.authorized() ? "Accepted" : normalizedStatus(authorization.status()));
            ObjectNode authorizeResponse = objectMapper.createObjectNode();
            authorizeResponse.set("idTokenInfo", idTokenInfo);
            authorizeResponse.put("certificateStatus", authorization.certificateStatus() == null
                    ? "NoCertificateAvailable"
                    : authorization.certificateStatus());

            response.put("status", "Accepted");
            response.put("data", objectMapper.writeValueAsString(authorizeResponse));

            log.debug("DataTransfer response: status=Accepted");
            return response;
        } catch (Exception e) {
            log.error("Error handling DataTransfer: {}", e.getMessage(), e);
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "Rejected");
            return response;
        }
    }

    private JsonNode parseData(JsonNode data) throws Exception {
        if (data.isTextual()) {
            return objectMapper.readTree(data.asText());
        }
        return data;
    }

    private String normalizedStatus(String status) {
        if (status == null) {
            return "Invalid";
        }
        return switch (status.trim().toUpperCase()) {
            case "ACCEPTED" -> "Accepted";
            case "BLOCKED" -> "Blocked";
            case "EXPIRED" -> "Expired";
            case "CONCURRENT_TX" -> "ConcurrentTx";
            case "NO_CREDIT" -> "NoCredit";
            default -> "Invalid";
        };
    }

}
