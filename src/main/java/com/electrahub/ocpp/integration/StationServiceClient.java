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
public class StationServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(StationServiceClient.class);


    private final RestClient restClient;
    private final String baseUrl;

    public StationServiceClient(
            RestClient.Builder restClientBuilder,
            /**
             * Executes value for `StationServiceClient`.
             *
             * <p>Detailed behavior: follows the current implementation path and
             * enforces component-specific rules in `com.electrahub.ocpp.integration`.
             * @param baseUrl input consumed by Value.
             * @return result produced by Value.
             */
            @Value("${integration.station-service.base-url}") String baseUrl) {
                LOGGER.info("CODEx_ENTRY_LOG: Entering StationServiceClient#Value");
                LOGGER.debug("CODEx_ENTRY_LOG: Entering StationServiceClient#Value with debug context");
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Retrieves get station for `StationServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param chargePointId input consumed by getStation.
     * @return result produced by getStation.
     */
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

    /**
     * Updates update connector status for `StationServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param chargePointId input consumed by updateConnectorStatus.
     * @param connectorId input consumed by updateConnectorStatus.
     * @param status input consumed by updateConnectorStatus.
     */
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

    /**
     * Retrieves get connector for `StationServiceClient`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.integration`.
     * @param chargePointId input consumed by getConnector.
     * @param connectorId input consumed by getConnector.
     * @return result produced by getConnector.
     */
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
