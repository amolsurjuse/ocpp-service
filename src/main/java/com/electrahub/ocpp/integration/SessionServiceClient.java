package com.electrahub.ocpp.integration;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class SessionServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionServiceClient.class);


    private final RestClient restClient;
    private final String baseUrl;

    public SessionServiceClient(
            RestClient.Builder restClientBuilder,
            /**
             * Executes value for `SessionServiceClient`.
             *
             * <p>Detailed behavior: follows the current implementation path and
             * enforces component-specific rules in `com.electrahub.ocpp.integration`.
             * @param baseUrl input consumed by Value.
             * @return result produced by Value.
             */
            @Value("${integration.session-service.base-url}") String baseUrl) {
                LOGGER.info("CODEx_ENTRY_LOG: Entering SessionServiceClient#Value");
                LOGGER.debug("CODEx_ENTRY_LOG: Entering SessionServiceClient#Value with debug context");
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Executes authorize for `SessionServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param idTag input consumed by authorize.
     * @return result produced by authorize.
     */
    public boolean authorize(String idTag) {
        try {
            JsonNode response = restClient.post()
                .uri("/api/v1/sessions/authorize")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new java.util.HashMap<String, String>() {{
                    put("idTag", idTag);
                }})
                .retrieve()
                .body(JsonNode.class);

            boolean authorized = response.path("authorized").asBoolean(false);
            log.debug("Authorization result for {}: {}", idTag, authorized);
            return authorized;
        } catch (Exception e) {
            log.error("Error authorizing idTag: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Creates start session for `SessionServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param request input consumed by startSession.
     * @return result produced by startSession.
     */
    public JsonNode startSession(JsonNode request) {
        try {
            return restClient.post()
                .uri("/api/v1/sessions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Error starting session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start session", e);
        }
    }

    /**
     * Executes stop session for `SessionServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param transactionId input consumed by stopSession.
     * @param request input consumed by stopSession.
     * @return result produced by stopSession.
     */
    public JsonNode stopSession(int transactionId, JsonNode request) {
        try {
            return restClient.post()
                .uri("/api/v1/sessions/{transactionId}/stop", transactionId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Error stopping session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop session", e);
        }
    }

    /**
     * Creates add meter values for `SessionServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param transactionId input consumed by addMeterValues.
     * @param meterValues input consumed by addMeterValues.
     */
    public void addMeterValues(int transactionId, JsonNode meterValues) {
        try {
            restClient.post()
                .uri("/api/v1/sessions/{transactionId}/meter-values", transactionId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(meterValues)
                .retrieve()
                .toBodilessEntity();

            log.debug("Added meter values for transaction: {}", transactionId);
        } catch (Exception e) {
            log.error("Error adding meter values: {}", e.getMessage(), e);
        }
    }

}
