package com.electrahub.ocpp.handler;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppAuthorizationGrantService;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthorizeHandler implements OcppMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizeHandler.class);


    private final SessionServiceClient sessionServiceClient;
    private final OcppAuthorizationGrantService authorizationGrants;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes authorize handler for `AuthorizeHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param sessionServiceClient input consumed by AuthorizeHandler.
     */
    public AuthorizeHandler(
            SessionServiceClient sessionServiceClient,
            OcppAuthorizationGrantService authorizationGrants
    ) {
        LOGGER.info(" Entering AuthorizeHandler#AuthorizeHandler");
        LOGGER.debug(" Entering AuthorizeHandler#AuthorizeHandler with debug context");
        this.sessionServiceClient = sessionServiceClient;
        this.authorizationGrants = authorizationGrants;
    }

    /**
     * Retrieves get action for `AuthorizeHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @return result produced by getAction.
     */
    @Override
    public String getAction() {
        return "Authorize";
    }

    /**
     * Processes handle for `AuthorizeHandler`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.handler`.
     * @param chargePointId input consumed by handle.
     * @param payload input consumed by handle.
     * @return result produced by handle.
     */
    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        boolean ocpp201 = payload.has("idToken");
        try {
            // Support both OCPP 1.6 (idTag) and 2.0.1 (idToken)
            String idTag = payload.path("idTag").asText();
            String idTokenType = null;
            if (idTag.isEmpty()) {
                idTag = payload.path("idToken").path("idToken").asText();
                idTokenType = payload.path("idToken").path("type").asText(null);
            }
            String certificate = payload.path("certificate").asText(null);

            log.info("Authorizing idTag: {} for charge point: {}", idTag, chargePointId);

            // Call session service to authorize
            SessionServiceClient.AuthorizationResult authorization = sessionServiceClient.authorize(idTag, idTokenType, certificate);

            boolean accepted = authorization.authorized();
            if (accepted && !authorizationGrants.grantAuthorization(chargePointId, idTag)) {
                accepted = false;
                log.warn("Rejecting OCPP authorization for charge point {} because the one-time start grant could not be stored", chargePointId);
            }

            ObjectNode tokenInfo = objectMapper.createObjectNode();
            tokenInfo.put("status", accepted ? "Accepted" : normalizedStatus(authorization.status(), ocpp201));

            ObjectNode response = objectMapper.createObjectNode();
            response.set(ocpp201 ? "idTokenInfo" : "idTagInfo", tokenInfo);
            if (ocpp201 && certificate != null && !certificate.isBlank()) {
                response.put("certificateStatus", authorization.certificateStatus() == null
                        ? "NoCertificateAvailable"
                        : authorization.certificateStatus());
            }

            log.debug("Authorization response: status={} for idTag: {}",
                tokenInfo.get("status").asText(), idTag);
            return response;
        } catch (Exception e) {
            log.error("Error handling Authorize: {}", e.getMessage(), e);
            ObjectNode tokenInfo = objectMapper.createObjectNode();
            tokenInfo.put("status", "Invalid");
            ObjectNode response = objectMapper.createObjectNode();
            response.set(ocpp201 ? "idTokenInfo" : "idTagInfo", tokenInfo);
            if (ocpp201 && payload.hasNonNull("certificate")) {
                response.put("certificateStatus", "NoCertificateAvailable");
            }
            return response;
        }
    }

    private String normalizedStatus(String status, boolean ocpp201) {
        if (status == null) {
            return "Invalid";
        }
        return switch (status.trim().toUpperCase()) {
            case "ACCEPTED" -> "Accepted";
            case "BLOCKED" -> "Blocked";
            case "EXPIRED" -> "Expired";
            case "CONCURRENT_TX" -> "ConcurrentTx";
            case "NO_CREDIT" -> ocpp201 ? "NoCredit" : "Invalid";
            default -> "Invalid";
        };
    }

}
