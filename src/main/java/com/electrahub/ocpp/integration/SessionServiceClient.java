package com.electrahub.ocpp.integration;

import com.electrahub.ocpp.config.InternalServiceTokenFilter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class SessionServiceClient {

    private final RestClient restClient;
    private final String internalToken;

    public SessionServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.session-service.base-url}") String baseUrl,
            @Value("${app.security.internal-token:${APP_SECURITY_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        log.info("Session service client configured baseUrl={} internalTokenConfigured={}", baseUrl, !this.internalToken.isBlank());
    }

    public boolean authorize(String idTag) {
        try {
            JsonNode response = restClient.post()
                    .uri("/api/v1/sessions/authorize")
                    .header(InternalServiceTokenFilter.HEADER_NAME, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("idTag", idTag))
                    .retrieve()
                    .body(JsonNode.class);
            return response != null && response.path("authorized").asBoolean(false);
        } catch (Exception ex) {
            log.warn("Authorize callback failed for idTag={} summary={}", idTag, callbackFailureSummary(ex));
            log.debug("Authorize callback failure details for idTag={}", idTag, ex);
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
                .header(InternalServiceTokenFilter.HEADER_NAME, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void onStopTransaction(int transactionId, Integer meterStop, String timestamp, String reason) {
        onStopTransaction(transactionId, null, null, meterStop, timestamp, reason);
    }

    public void onStopTransaction(
            int transactionId,
            String chargePointId,
            Integer connectorId,
            Integer meterStop,
            String timestamp,
            String reason
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (chargePointId != null && !chargePointId.isBlank()) {
                payload.put("chargePointId", chargePointId);
            }
            if (connectorId != null && connectorId > 0) {
                payload.put("connectorId", connectorId);
            }
            payload.put("meterStop", meterStop == null ? 0 : meterStop);
            payload.put("timestamp", blankToNull(timestamp));
            payload.put("reason", nullSafe(reason, "Local"));

            restClient.post()
                    .uri("/api/v1/sessions/ocpp/stop-transaction/{transactionId}", transactionId)
                    .header(InternalServiceTokenFilter.HEADER_NAME, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("StopTransaction callback failed for transactionId={} summary={}", transactionId, callbackFailureSummary(ex));
            log.debug("StopTransaction callback failure details for transactionId={}", transactionId, ex);
            throw ex;
        }
    }

    public void onMeterValues(
            String chargePointId,
            int transactionId,
            Integer connectorId,
            String timestamp,
            BigDecimal energyWh,
            BigDecimal powerW
    ) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chargePointId", nullSafe(chargePointId, "unknown"));
            payload.put("connectorId", connectorId == null ? 0 : connectorId);
            payload.put("timestamp", blankToNull(timestamp));
            payload.put("energyWh", energyWh == null ? BigDecimal.ZERO : energyWh);
            payload.put("powerW", powerW == null ? BigDecimal.ZERO : powerW);

            restClient.post()
                    .uri("/api/v1/sessions/ocpp/meter-values/{transactionId}", transactionId)
                    .header(InternalServiceTokenFilter.HEADER_NAME, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            if (isMissingSessionCallback(ex)) {
                log.debug("MeterValues callback ignored for unknown transactionId={} summary={}",
                        transactionId, callbackFailureSummary(ex));
                return;
            }
            log.warn("MeterValues callback failed for transactionId={} summary={}", transactionId, callbackFailureSummary(ex));
            log.debug("MeterValues callback failure details for transactionId={}", transactionId, ex);
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
                    .header(InternalServiceTokenFilter.HEADER_NAME, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("StatusNotification callback failed for chargePointId={}, connectorId={} summary={}",
                    chargePointId, connectorId, callbackFailureSummary(ex));
            log.debug("StatusNotification callback failure details for chargePointId={}, connectorId={}",
                    chargePointId, connectorId, ex);
        }
    }

    public static boolean isExpectedSessionCallbackFailure(Exception ex) {
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 404 || status == 409 || status == 503 || status == 504;
        }
        return false;
    }

    public static String callbackFailureSummary(Exception ex) {
        if (ex instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (body.length() > 240) {
                body = body.substring(0, 240) + "...";
            }
            return "status=%s body=%s".formatted(responseException.getStatusCode().value(), body);
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    private boolean isMissingSessionCallback(Exception ex) {
        return ex instanceof RestClientResponseException responseException
                && responseException.getStatusCode().value() == 404;
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
