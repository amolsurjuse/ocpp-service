package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizeHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final AuthorizeHandler handler = new AuthorizeHandler(sessionServiceClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsNativeOcpp201PlugAndChargeAuthorizationFields() throws Exception {
        when(sessionServiceClient.authorize("US-EHB-C12345678", "eMAID", "CERT-EHB-001"))
                .thenReturn(new SessionServiceClient.AuthorizationResult(true, "Accepted", null, "Accepted"));
        JsonNode request = objectMapper.readTree("""
                {
                  "idToken": {"idToken": "US-EHB-C12345678", "type": "eMAID"},
                  "certificate": "CERT-EHB-001"
                }
                """);

        JsonNode response = handler.handle("EH-US-CHG-0201", request);

        assertEquals("Accepted", response.path("idTokenInfo").path("status").asText());
        assertEquals("Accepted", response.path("certificateStatus").asText());
        verify(sessionServiceClient).authorize("US-EHB-C12345678", "eMAID", "CERT-EHB-001");
    }

    @Test
    void mapsInternalAuthorizationStatusToOcppStatus() throws Exception {
        when(sessionServiceClient.authorize("RFID-BLOCKED", null, null))
                .thenReturn(new SessionServiceClient.AuthorizationResult(false, "BLOCKED", "Authorization rejected", null));

        JsonNode response = handler.handle("EH-US-CHG-0016", objectMapper.readTree("""
                {"idTag":"RFID-BLOCKED"}
                """));

        assertEquals("Blocked", response.path("idTagInfo").path("status").asText());
    }

    @Test
    void failsClosedWithTheOcpp201ResponseShapeWhenAuthorizationThrows() throws Exception {
        when(sessionServiceClient.authorize("US-EHB-ERROR", "eMAID", "CERT-ERROR"))
                .thenThrow(new IllegalStateException("unavailable"));

        JsonNode response = handler.handle("EH-US-CHG-0201", objectMapper.readTree("""
                {
                  "idToken": {"idToken": "US-EHB-ERROR", "type": "eMAID"},
                  "certificate": "CERT-ERROR"
                }
                """));

        assertEquals("Invalid", response.path("idTokenInfo").path("status").asText());
        assertEquals("NoCertificateAvailable", response.path("certificateStatus").asText());
    }
}
