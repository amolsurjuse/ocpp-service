package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.exception.OcppCallErrorException;
import com.electrahub.ocpp.integration.ChargingStationTenantResolver;
import com.electrahub.ocpp.integration.PncCertificateInstallationClient;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Get15118EVCertificateHandlerTest {
    private final ConnectionManager connections = mock(ConnectionManager.class);
    private final ChargingStationTenantResolver tenants = mock(ChargingStationTenantResolver.class);
    private final PncCertificateInstallationClient pnc = mock(PncCertificateInstallationClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private final Get15118EVCertificateHandler handler =
            new Get15118EVCertificateHandler(connections, tenants, pnc, json);

    @Test
    void forwardsAnOpaqueInstallRequestAndReturnsTheExiResponse() throws Exception {
        String requestExi = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        String responseExi = Base64.getEncoder().encodeToString(new byte[] {4, 5, 6});
        when(connections.getProtocol("EH-US-CHG-0003")).thenReturn("OCPP201");
        when(tenants.resolve("EH-US-CHG-0003")).thenReturn("electrahub");
        when(pnc.install(eq("electrahub"), any())).thenReturn(new PncCertificateInstallationClient.Result(true, responseExi));

        JsonNode response = handler.handle("EH-US-CHG-0003", "message-42", request(requestExi));

        assertEquals("Accepted", response.path("status").asText());
        assertEquals(responseExi, response.path("exiResponse").asText());
        verify(pnc).install("electrahub", new PncCertificateInstallationClient.Request(
                "EH-US-CHG-0003", "message-42", "Install", Get15118EVCertificateHandler.SCHEMA, requestExi));
    }

    @Test
    void mapsAProviderFailureToADataFreeFailedResponse() throws Exception {
        when(connections.getProtocol("EH-US-CHG-0003")).thenReturn("OCPP201");
        when(tenants.resolve("EH-US-CHG-0003")).thenReturn("electrahub");
        when(pnc.install(eq("electrahub"), any())).thenReturn(new PncCertificateInstallationClient.Result(false, null));

        JsonNode response = handler.handle("EH-US-CHG-0003", "message-43", request("AQID"));

        assertEquals("Failed", response.path("status").asText());
        assertFalse(response.has("exiResponse"));
    }

    @Test
    void rejectsNonOcpp201ConnectionsBeforeTenantOrProviderAccess() throws Exception {
        when(connections.getProtocol("EH-US-CHG-0003")).thenReturn("OCPP16");

        OcppCallErrorException failure = assertThrows(OcppCallErrorException.class,
                () -> handler.handle("EH-US-CHG-0003", "message-44", request("AQID")));

        assertEquals("NotSupported", failure.errorCode());
        verify(tenants, never()).resolve(any());
        verify(pnc, never()).install(any(), any());
    }

    @Test
    void rejectsMalformedAndUnknownFieldsBeforeProviderAccess() throws Exception {
        when(connections.getProtocol("EH-US-CHG-0003")).thenReturn("OCPP201");
        JsonNode payload = json.readTree("""
                {"action":"Install","iso15118SchemaVersion":"urn:iso:15118:2:2013:MsgDef",
                 "exiRequest":"not base64","unexpected":"secret"}
                """);

        OcppCallErrorException failure = assertThrows(OcppCallErrorException.class,
                () -> handler.handle("EH-US-CHG-0003", "message-45", payload));

        assertEquals("FormationViolation", failure.errorCode());
        verify(tenants, never()).resolve(any());
        verify(pnc, never()).install(any(), any());
    }

    private JsonNode request(String exi) throws Exception {
        return json.readTree("""
                {"action":"Install","iso15118SchemaVersion":"urn:iso:15118:2:2013:MsgDef","exiRequest":"%s"}
                """.formatted(exi));
    }
}
