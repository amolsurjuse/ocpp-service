package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.PncCertificateInstallationClient;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Get15118EvCertificateHandlerTest {
    private final PncCertificateInstallationClient pnc = mock(PncCertificateInstallationClient.class);
    private final ConnectionManager connections = mock(ConnectionManager.class);
    private final ObjectMapper json = new ObjectMapper();
    private final Get15118EvCertificateHandler handler =
            new Get15118EvCertificateHandler(pnc, connections, json);

    @Test
    void mapsAcceptedPncResponseToNativeOcpp201Shape() throws Exception {
        when(connections.getProtocol("CP-201-1")).thenReturn("OCPP201");
        String exiResponse = encoded("signed-response");
        when(pnc.install(any())).thenReturn(new PncCertificateInstallationClient.Response(
                "Accepted", exiResponse, null, UUID.randomUUID()));

        JsonNode response = handler.handle("CP-201-1", "message-1", request(encoded("signed-request")));

        assertEquals("Accepted", response.path("status").asText());
        assertEquals(exiResponse, response.path("exiResponse").asText());
        verify(pnc).install(new PncCertificateInstallationClient.Request(
                "CP-201-1", "message-1", "Install", Get15118EvCertificateHandler.SCHEMA_15118_2,
                encoded("signed-request")));
    }

    @Test
    void providerFailureAndMalformedAcceptedResponseFailClosed() throws Exception {
        when(connections.getProtocol("CP-201-2")).thenReturn("OCPP201");
        when(pnc.install(any()))
                .thenReturn(new PncCertificateInstallationClient.Response(
                        "Failed", null, "PROVIDER_UNAVAILABLE", UUID.randomUUID()))
                .thenReturn(new PncCertificateInstallationClient.Response(
                        "Accepted", "not-base64%%%", null, UUID.randomUUID()));

        assertEquals("Failed", handler.handle("CP-201-2", "message-2", request(encoded("one")))
                .path("status").asText());
        assertEquals("Failed", handler.handle("CP-201-2", "message-3", request(encoded("two")))
                .path("status").asText());
    }

    @Test
    void rejectsNon201AndInvalidPayloadBeforeCallingPnc() throws Exception {
        when(connections.getProtocol("CP-16")).thenReturn("OCPP16J");
        assertEquals("Failed", handler.handle("CP-16", "message-4", request(encoded("request")))
                .path("status").asText());

        when(connections.getProtocol("CP-201-3")).thenReturn("OCPP201");
        JsonNode invalid = request("not-base64%%%");
        assertEquals("Failed", handler.handle("CP-201-3", "message-5", invalid).path("status").asText());
        assertEquals("Failed", handler.handle("CP-201-3", "message-6", request("dGVzdA"))
                .path("status").asText());
        verify(pnc, never()).install(any());
    }

    private JsonNode request(String exi) throws Exception {
        return json.readTree("""
                {"action":"Install","iso15118SchemaVersion":"urn:iso:15118:2:2013:MsgDef","exiRequest":"%s"}
                """.formatted(exi));
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
