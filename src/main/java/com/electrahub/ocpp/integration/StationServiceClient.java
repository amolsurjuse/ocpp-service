package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class StationServiceClient {

    private final RestClient restClient;
    private final String baseUrl;

    public StationServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.station-service.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public JsonNode getStation(String chargePointId) {
        try {
            return restClient.get()
                .uri("/api/v1/stations/{chargePointId}", chargePointId)
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching station: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch station: " + chargePointId, e);
        }
    }

    public void updateConnectorStatus(String chargePointId, int connectorId, String status) {
        try {
            restClient.post()
                .uri("/api/v1/stations/{chargePointId}/connectors/{connectorId}/status", chargePointId, connectorId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new java.util.HashMap<String, String>() {{
                    put("status", status);
                }})
                .retrieve()
                .toBodilessEntity();

            log.debug("Updated connector status for {}/{}: {}", chargePointId, connectorId, status);
        } catch (Exception e) {
            log.error("Error updating connector status: {}", e.getMessage(), e);
        }
    }

    public JsonNode getConnector(String chargePointId, int connectorId) {
        try {
            return restClient.get()
                .uri("/api/v1/stations/{chargePointId}/connectors/{connectorId}", chargePointId, connectorId)
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching connector: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch connector", e);
        }
    }

}
