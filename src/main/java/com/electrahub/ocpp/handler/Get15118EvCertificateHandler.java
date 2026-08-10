package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.PncCertificateInstallationClient;
import com.electrahub.ocpp.service.OcppMessageHandler;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@Slf4j
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
public class Get15118EvCertificateHandler implements OcppMessageHandler {
    static final String SCHEMA_15118_2 = "urn:iso:15118:2:2013:MsgDef";
    private static final int MAX_EXI_BYTES = 262_144;
    private final PncCertificateInstallationClient pnc;
    private final ConnectionManager connections;
    private final ObjectMapper json;

    public Get15118EvCertificateHandler(PncCertificateInstallationClient pnc,
                                        ConnectionManager connections,
                                        ObjectMapper json) {
        this.pnc = pnc;
        this.connections = connections;
        this.json = json;
    }

    @Override
    public String getAction() {
        return "Get15118EVCertificate";
    }

    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        throw new IllegalArgumentException("OCPP message identity is required");
    }

    @Override
    public JsonNode handle(String chargePointId, String sourceMessageId, JsonNode payload) {
        try {
            validate(chargePointId, sourceMessageId, payload);
            PncCertificateInstallationClient.Response response = pnc.install(
                    new PncCertificateInstallationClient.Request(
                            chargePointId.trim(), sourceMessageId.trim(), payload.path("action").asText(),
                            payload.path("iso15118SchemaVersion").asText(), payload.path("exiRequest").asText()));
            if (response == null || !"Accepted".equals(response.status()) || !validExi(response.exiResponse())) {
                return failed();
            }
            ObjectNode accepted = json.createObjectNode();
            accepted.put("status", "Accepted");
            accepted.put("exiResponse", response.exiResponse());
            return accepted;
        } catch (Exception exception) {
            log.warn("Get15118EVCertificate failed closed for charge point {} and message {}: {}",
                    chargePointId, sourceMessageId, exception.getClass().getSimpleName());
            return failed();
        }
    }

    private void validate(String chargePointId, String sourceMessageId, JsonNode payload) {
        required(chargePointId, 128, "chargePointId");
        required(sourceMessageId, 128, "messageId");
        if (!"OCPP201".equalsIgnoreCase(connections.getProtocol(chargePointId))) {
            throw new IllegalArgumentException("OCPP 2.0.1 connection is required");
        }
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("payload is required");
        if (!"Install".equals(required(payload.path("action").asText(null), 16, "action"))) {
            throw new IllegalArgumentException("Only Install is supported");
        }
        if (!SCHEMA_15118_2.equals(required(payload.path("iso15118SchemaVersion").asText(null), 80, "schema"))) {
            throw new IllegalArgumentException("Unsupported ISO 15118 schema");
        }
        String exi = required(payload.path("exiRequest").asText(null), 400_000, "exiRequest");
        byte[] decoded = decode(exi);
        if (decoded.length == 0 || decoded.length > MAX_EXI_BYTES) {
            throw new IllegalArgumentException("EXI request size is invalid");
        }
    }

    private boolean validExi(String value) {
        if (value == null || value.isBlank() || value.length() > 400_000) return false;
        try {
            byte[] decoded = decode(value);
            return decoded.length > 0 && decoded.length <= MAX_EXI_BYTES;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] decode(String value) {
        byte[] decoded = Base64.getDecoder().decode(value);
        if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("EXI payload is not canonical Base64");
        }
        return decoded;
    }

    private ObjectNode failed() {
        ObjectNode response = json.createObjectNode();
        response.put("status", "Failed");
        return response;
    }

    private static String required(String value, int max, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required");
        String result = value.trim();
        if (result.length() > max || result.indexOf('\0') >= 0) throw new IllegalArgumentException(field + " is invalid");
        return result;
    }
}
