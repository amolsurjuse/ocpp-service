package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthorizeHandler implements OcppMessageHandler {

    private final SessionServiceClient sessionServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthorizeHandler(SessionServiceClient sessionServiceClient) {
        this.sessionServiceClient = sessionServiceClient;
    }

    @Override
    public String getAction() {
        return "Authorize";
    }

    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        try {
            // Support both OCPP 1.6 (idTag) and 2.0.1 (idToken)
            String idTag = payload.path("idTag").asText();
            if (idTag.isEmpty()) {
                idTag = payload.path("idToken").path("idToken").asText();
            }

            log.info("Authorizing idTag: {} for charge point: {}", idTag, chargePointId);

            // Call session service to authorize
            boolean authorized = sessionServiceClient.authorize(idTag);

            ObjectNode idTagInfo = objectMapper.createObjectNode();
            if (authorized) {
                idTagInfo.put("status", "Accepted");
            } else {
                idTagInfo.put("status", "Invalid");
            }

            ObjectNode response = objectMapper.createObjectNode();
            response.set("idTagInfo", idTagInfo);

            log.debug("Authorization response: status={} for idTag: {}",
                idTagInfo.get("status").asText(), idTag);
            return response;
        } catch (Exception e) {
            log.error("Error handling Authorize: {}", e.getMessage(), e);
            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Invalid");
            ObjectNode response = objectMapper.createObjectNode();
            response.set("idTagInfo", idTagInfo);
            return response;
        }
    }

}
