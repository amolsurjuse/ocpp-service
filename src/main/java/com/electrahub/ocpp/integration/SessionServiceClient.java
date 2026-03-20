package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class SessionServiceClient {

    private final RestClient restClient;
    private final String baseUrl;

    public SessionServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.session-service.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

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
