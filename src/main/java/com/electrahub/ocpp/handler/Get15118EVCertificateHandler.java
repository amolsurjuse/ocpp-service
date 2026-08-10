package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.exception.OcppCallErrorException;
import com.electrahub.ocpp.integration.ChargingStationTenantResolver;
import com.electrahub.ocpp.integration.PncCertificateInstallationClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
public class Get15118EVCertificateHandler implements OcppMessageHandler {
    static final String SCHEMA = "urn:iso:15118:2:2013:MsgDef";
    // Leaves room for the JSON-RPC envelope inside the 65,536-byte WebSocket text limit.
    private static final int MAX_EXI_BYTES = 48_000;
    private static final int MAX_EXI_BASE64_CHARS = 64_000;
    private static final Set<String> FIELDS = Set.of("action", "iso15118SchemaVersion", "exiRequest", "customData");

    private final ConnectionManager connections;
    private final ChargingStationTenantResolver tenants;
    private final PncCertificateInstallationClient pnc;
    private final ObjectMapper json;

    public Get15118EVCertificateHandler(ConnectionManager connections,
                                        ChargingStationTenantResolver tenants,
                                        PncCertificateInstallationClient pnc,
                                        ObjectMapper json) {
        this.connections = connections;
        this.tenants = tenants;
        this.pnc = pnc;
        this.json = json;
    }

    @Override
    public String getAction() {
        return "Get15118EVCertificate";
    }

    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        return handle(chargePointId, null, payload);
    }

    @Override
    public JsonNode handle(String chargePointId, String sourceMessageId, JsonNode payload) {
        requireOcpp201(chargePointId);
        String messageId = required(sourceMessageId, 128, "OCPP message ID");
        validateShape(payload);
        String action = required(payload.path("action").asText(null), 16, "action");
        if (!"Install".equals(action)) {
            throw error("PropertyConstraintViolation", "Only ISO 15118 certificate installation is supported");
        }
        String schema = required(payload.path("iso15118SchemaVersion").asText(null), 80, "schema");
        if (!SCHEMA.equals(schema)) {
            throw error("PropertyConstraintViolation", "The ISO 15118 schema version is not supported");
        }
        String exi = validExiRequest(payload.path("exiRequest").asText(null));
        String tenant = tenants.resolve(chargePointId);

        PncCertificateInstallationClient.Result result = pnc.install(tenant,
                new PncCertificateInstallationClient.Request(chargePointId, messageId, action, schema, exi));
        ObjectNode response = json.createObjectNode();
        response.put("status", result.accepted() ? "Accepted" : "Failed");
        if (result.accepted()) response.put("exiResponse", result.exiResponse());
        return response;
    }

    private void requireOcpp201(String chargePointId) {
        if (!"OCPP201".equalsIgnoreCase(connections.getProtocol(chargePointId))) {
            throw error("NotSupported", "Get15118EVCertificate requires OCPP 2.0.1");
        }
    }

    private static void validateShape(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw error("FormationViolation", "Get15118EVCertificate payload must be an object");
        }
        payload.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) {
                throw error("FormationViolation", "Get15118EVCertificate contains an unsupported field");
            }
        });
    }

    private static String validExiRequest(String value) {
        String encoded = required(value, MAX_EXI_BASE64_CHARS, "exiRequest");
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length == 0 || decoded.length > MAX_EXI_BYTES) throw new IllegalArgumentException();
            return encoded;
        } catch (IllegalArgumentException invalid) {
            throw error("PropertyConstraintViolation", "The EXI request encoding or size is invalid");
        }
    }

    private static String required(String value, int maximum, String field) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > maximum || result.indexOf('\0') >= 0) {
            throw error("FormationViolation", field + " is required or invalid");
        }
        return result;
    }

    private static OcppCallErrorException error(String code, String description) {
        return new OcppCallErrorException(code, description);
    }
}
