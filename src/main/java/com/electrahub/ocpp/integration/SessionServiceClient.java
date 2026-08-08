package com.electrahub.ocpp.integration;

import com.electrahub.ocpp.config.InternalServiceTokenFilter;
import com.electrahub.ocpp.messaging.OcppDeviceEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SessionServiceClient {

    private final RestClient restClient;
    private final String internalToken;
    private final OcppDeviceEventPublisher eventPublisher;
    private final boolean kafkaEnabled;
    private final boolean legacyCallbacksEnabled;

    @Autowired
    public SessionServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${integration.session-service.base-url}") String baseUrl,
            @Value("${app.security.internal-token:${APP_SECURITY_INTERNAL_TOKEN:}}") String internalToken,
            Optional<OcppDeviceEventPublisher> eventPublisher,
            @Value("${app.ocpp-events.kafka-enabled:false}") boolean kafkaEnabled,
            @Value("${app.ocpp-events.legacy-session-callbacks-enabled:true}") boolean legacyCallbacksEnabled
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        this.eventPublisher = eventPublisher.orElse(null);
        this.kafkaEnabled = kafkaEnabled;
        this.legacyCallbacksEnabled = legacyCallbacksEnabled;
        log.info("Session service client configured baseUrl={} internalTokenConfigured={}", baseUrl, !this.internalToken.isBlank());
    }

    /** Test/legacy constructor; production wiring uses the feature-gated constructor above. */
    public SessionServiceClient(RestClient.Builder restClientBuilder, String baseUrl, String internalToken) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        this.eventPublisher = null;
        this.kafkaEnabled = false;
        this.legacyCallbacksEnabled = true;
    }

    public boolean authorize(String idTag) {
        return authorize(idTag, null, null).authorized();
    }

    public AuthorizationResult authorize(String idTag, String idTokenType, String contractCertificate) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("idTag", idTag);
            if (idTokenType != null && !idTokenType.isBlank()) {
                payload.put("idTokenType", idTokenType);
            }
            if (contractCertificate != null && !contractCertificate.isBlank()) {
                payload.put("contractCertificate", contractCertificate);
            }
            AuthorizationResponse response = restClient.post()
                    .uri("/api/v1/sessions/authorize")
                    .header(InternalServiceTokenFilter.HEADER_NAME, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(AuthorizationResponse.class);
            if (response == null) {
                return new AuthorizationResult(false, "Invalid", "No authorization response", "NoCertificateAvailable");
            }
            return new AuthorizationResult(response.authorized(), response.status(), response.reason(), response.certificateStatus());
        } catch (Exception ex) {
            log.warn("Authorize callback failed: summary={}", callbackFailureSummary(ex));
            log.debug("Authorize callback failure details", ex);
            return new AuthorizationResult(false, "Invalid", callbackFailureSummary(ex), "NoCertificateAvailable");
        }
    }

    public void onStartTransaction(
            String chargePointId,
            Integer connectorId,
            String idTag,
            String idTokenType,
            Integer meterStart,
            String timestamp,
            Integer transactionId
    ) {
        onStartTransaction(chargePointId, connectorId, idTag, idTokenType, meterStart, timestamp, transactionId, null);
    }

    public void onStartTransaction(
            String chargePointId,
            Integer connectorId,
            String idTag,
            String idTokenType,
            Integer meterStart,
            String timestamp,
            Integer transactionId,
            String correlationId
    ) {
        onStartTransaction(chargePointId, connectorId, idTag, idTokenType, meterStart,
                timestamp, transactionId, correlationId, null);
    }

    public void onStartTransaction(
            String chargePointId, Integer connectorId, String idTag, String idTokenType,
            Integer meterStart, String timestamp, Integer transactionId,
            String correlationId, String sourceMessageId
    ) {
        onStartTransaction(chargePointId, connectorId, idTag, idTokenType, meterStart, timestamp,
                transactionId, null, correlationId, sourceMessageId);
    }

    public void onStartTransaction(
            String chargePointId, Integer connectorId, String idTag, String idTokenType,
            Integer meterStart, String timestamp, Integer transactionId, String nativeTransactionId,
            String correlationId, String sourceMessageId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chargePointId", nullSafe(chargePointId, "unknown"));
        payload.put("connectorId", connectorId == null ? 0 : connectorId);
        payload.put("idTag", nullSafe(idTag, ""));
        if (idTokenType != null && !idTokenType.isBlank()) {
            payload.put("idTokenType", idTokenType);
        }
        payload.put("meterStart", meterStart == null ? 0 : meterStart);
        payload.put("timestamp", blankToNull(timestamp));
        payload.put("transactionId", transactionId == null ? 0 : transactionId);
        if (nativeTransactionId != null && !nativeTransactionId.isBlank()) {
            payload.put("nativeTransactionId", nativeTransactionId.trim());
        }
        if (correlationId != null && !correlationId.isBlank()) payload.put("correlationId", correlationId);

        publishIfEnabled("StartTransaction", chargePointId, connectorId, sourceMessageId, payload);
        if (!legacyCallbacksEnabled) return;

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
        onStopTransaction(transactionId, chargePointId, connectorId, meterStop, timestamp, reason, null);
    }

    public void onStopTransaction(
            int transactionId, String chargePointId, Integer connectorId, Integer meterStop,
            String timestamp, String reason, String sourceMessageId
    ) {
        onStopTransaction(transactionId, chargePointId, connectorId, meterStop, timestamp,
                reason, sourceMessageId, null);
    }

    public void onStopTransaction(
            int transactionId, String chargePointId, Integer connectorId, Integer meterStop,
            String timestamp, String reason, String sourceMessageId, Object signedMeterValues
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
            payload.put("transactionId", transactionId);
            if (signedMeterValues != null) payload.put("signedMeterValues", signedMeterValues);

            publishIfEnabled("StopTransaction", chargePointId, connectorId, sourceMessageId, payload);
            if (!legacyCallbacksEnabled) return;

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
            BigDecimal powerW,
            BigDecimal stateOfChargePercent
    ) {
        onMeterValues(chargePointId, transactionId, connectorId, timestamp,
                energyWh, powerW, stateOfChargePercent, null, null);
    }

    public void onMeterValues(
            String chargePointId, int transactionId, Integer connectorId, String timestamp,
            BigDecimal energyWh, BigDecimal powerW, BigDecimal stateOfChargePercent,
            String sourceMessageId, Object signedMeterValues
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chargePointId", nullSafe(chargePointId, "unknown"));
        payload.put("connectorId", connectorId == null ? 0 : connectorId);
        payload.put("transactionId", transactionId);
        payload.put("timestamp", blankToNull(timestamp));
        if (energyWh != null) payload.put("energyWh", energyWh);
        if (powerW != null) payload.put("powerW", powerW);
        if (stateOfChargePercent != null) payload.put("stateOfChargePercent", stateOfChargePercent);
        if (signedMeterValues != null) payload.put("signedMeterValues", signedMeterValues);
        publishIfEnabled("MeterValues", chargePointId, connectorId, sourceMessageId, payload);
        if (!legacyCallbacksEnabled) return;

        try {
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
            Integer transactionId,
            boolean endSessionRequested
    ) {
        onStatusNotification(chargePointId, connectorId, status, errorCode, timestamp,
                transactionId, endSessionRequested, null);
    }

    public void onStatusNotification(
            String chargePointId, Integer connectorId, String status, String errorCode,
            String timestamp, Integer transactionId, boolean endSessionRequested,
            String sourceMessageId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chargePointId", nullSafe(chargePointId, "unknown"));
        payload.put("connectorId", connectorId == null ? 0 : connectorId);
        payload.put("status", nullSafe(status, "Unavailable"));
        payload.put("errorCode", nullSafe(errorCode, "NoError"));
        payload.put("timestamp", blankToNull(timestamp));
        payload.put("transactionId", transactionId);
        payload.put("endSessionRequested", endSessionRequested);
        publishIfEnabled("StatusNotification", chargePointId, connectorId, sourceMessageId, payload);
        if (!legacyCallbacksEnabled) return;

        try {
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

    private void publishIfEnabled(String eventType, String chargePointId, Integer connectorId, Map<String, Object> payload) {
        publishIfEnabled(eventType, chargePointId, connectorId, null, payload);
    }

    private void publishIfEnabled(String eventType, String chargePointId, Integer connectorId,
                                  String sourceMessageId, Map<String, Object> payload) {
        if (kafkaEnabled) {
            if (eventPublisher == null) throw new IllegalStateException("Kafka OCPP event publisher is not configured");
            eventPublisher.publish(eventType, chargePointId, connectorId, sourceMessageId, payload);
        }
    }

    private record AuthorizationResponse(boolean authorized, String status, String reason, String certificateStatus) {
    }

    public record AuthorizationResult(boolean authorized, String status, String reason, String certificateStatus) {
    }
}
