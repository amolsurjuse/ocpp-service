package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class SessionServiceClient {

    private final RestClient restClient;

    public SessionServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.session-service.base-url}") String baseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public boolean authorize(String idTag) {
        try {
            JsonNode response = restClient.post()
                    .uri("/api/v1/sessions/authorize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("idTag", idTag))
                    .retrieve()
                    .body(JsonNode.class);
            return response != null && response.path("authorized").asBoolean(false);
        } catch (Exception ex) {
            log.warn("Authorize callback failed for idTag={}", idTag, ex);
            return false;
        }
    }

    public void onStartTransaction(
            String chargePointId,
            Integer connectorId,
            String idTag,
            Integer meterStart,
            String timestamp,
            Integer transactionId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chargePointId", nullSafe(chargePointId, "unknown"));
        payload.put("connectorId", connectorId == null ? 0 : connectorId);
        payload.put("idTag", nullSafe(idTag, ""));
        payload.put("meterStart", meterStart == null ? 0 : meterStart);
        payload.put("timestamp", blankToNull(timestamp));
        payload.put("transactionId", transactionId == null ? 0 : transactionId);

        restClient.post()
                .uri("/api/v1/sessions/ocpp/start-transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void onStopTransaction(int transactionId, Integer meterStop, String timestamp, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("meterStop", meterStop == null ? 0 : meterStop);
            payload.put("timestamp", blankToNull(timestamp));
            payload.put("reason", nullSafe(reason, "Local"));

            restClient.post()
                    .uri("/api/v1/sessions/ocpp/stop-transaction/{transactionId}", transactionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("StopTransaction callback failed for transactionId={}", transactionId, ex);
            throw ex;
        }
    }

    public void onMeterValues(
            int transactionId,
            Integer connectorId,
            String timestamp,
            BigDecimal energyWh,
            BigDecimal powerW
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("connectorId", connectorId == null ? 0 : connectorId);
            payload.put("timestamp", blankToNull(timestamp));
            payload.put("energyWh", energyWh == null ? BigDecimal.ZERO : energyWh);
            payload.put("powerW", powerW == null ? BigDecimal.ZERO : powerW);

            restClient.post()
                    .uri("/api/v1/sessions/ocpp/meter-values/{transactionId}", transactionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("MeterValues callback failed for transactionId={}", transactionId, ex);
        }
    }

    public void onStatusNotification(
            String chargePointId,
            Integer connectorId,
            String status,
            String errorCode,
            String timestamp,
            Integer transactionId
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chargePointId", nullSafe(chargePointId, "unknown"));
            payload.put("connectorId", connectorId == null ? 0 : connectorId);
            payload.put("status", nullSafe(status, "Unavailable"));
            payload.put("errorCode", nullSafe(errorCode, "NoError"));
            payload.put("timestamp", blankToNull(timestamp));
            payload.put("transactionId", transactionId);

            restClient.post()
                    .uri("/api/v1/sessions/ocpp/status-notification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("StatusNotification callback failed for chargePointId={}, connectorId={}", chargePointId, connectorId, ex);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String nullSafe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
