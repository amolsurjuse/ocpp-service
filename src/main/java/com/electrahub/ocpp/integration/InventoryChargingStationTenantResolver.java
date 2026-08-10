package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
public class InventoryChargingStationTenantResolver implements ChargingStationTenantResolver {
    private static final String INTERNAL_TOKEN_HEADER = "X-ElectraHub-Internal-Token";
    private final RestClient client;
    private final String internalToken;
    private final ObjectMapper json;

    public InventoryChargingStationTenantResolver(
            RestClient.Builder builder,
            ObjectMapper json,
            @Value("${integration.station-service.base-url}") String baseUrl,
            @Value("${app.security.internal-token:}") String internalToken) {
        if (internalToken == null || internalToken.isBlank() || internalToken.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("APP_SECURITY_INTERNAL_TOKEN is required for PnC tenant resolution");
        }
        this.client = builder.baseUrl(baseUrl).build();
        this.json = json;
        this.internalToken = internalToken.trim();
    }

    @Override
    public String resolveTenant(String chargingStationId) {
        String station = required(chargingStationId, 128, "chargingStationId").toUpperCase(java.util.Locale.ROOT);
        String body = client.post()
                .uri("/api/v1/internal/ownership/resolve")
                .header(INTERNAL_TOKEN_HEADER, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chargerId", station))
                .retrieve()
                .body(String.class);
        JsonNode response;
        try { response = json.readTree(body); }
        catch (Exception invalid) { throw new IllegalStateException("Inventory ownership response is invalid", invalid); }
        if (response == null || !response.isObject()) throw new IllegalStateException("Inventory ownership response is invalid");
        if (response == null || !station.equalsIgnoreCase(response.path("chargerId").asText())) {
            throw new IllegalStateException("Inventory ownership response does not match the charging station");
        }
        return required(response.path("tenantId").asText(null), 64, "tenantId");
    }

    private static String required(String value, int max, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required");
        String result = value.trim();
        if (result.length() > max || !result.matches("[A-Za-z0-9][A-Za-z0-9._:@-]*")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return result;
    }
}
