package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataTransferHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final DataTransferHandler handler = new DataTransferHandler(sessionServiceClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorizesOcpp16PlugAndChargeUsingTheOcaDataTransferContract() throws Exception {
        when(sessionServiceClient.authorize("US-EHB-C12345678", "eMAID", "CERT-EHB-001"))
                .thenReturn(new SessionServiceClient.AuthorizationResult(true, "Accepted", null, "Accepted"));
        JsonNode request = objectMapper.readTree("""
                {
                  "vendorId": "org.openchargealliance.iso15118pnc",
                  "messageId": "Authorize",
                  "data": "{\\\"idToken\\\":{\\\"idToken\\\":\\\"US-EHB-C12345678\\\",\\\"type\\\":\\\"eMAID\\\"},\\\"certificate\\\":\\\"CERT-EHB-001\\\"}"
                }
                """);

        JsonNode response = handler.handle("EH-US-CHG-0003", request);
        JsonNode wrappedResponse = objectMapper.readTree(response.path("data").asText());

        assertEquals("Accepted", response.path("status").asText());
        assertEquals("Accepted", wrappedResponse.path("idTokenInfo").path("status").asText());
        assertEquals("Accepted", wrappedResponse.path("certificateStatus").asText());
        verify(sessionServiceClient).authorize("US-EHB-C12345678", "eMAID", "CERT-EHB-001");
    }

    @Test
    void returnsAuthorizationRejectionInsideAnAcceptedDataTransferEnvelope() throws Exception {
        when(sessionServiceClient.authorize("US-EHB-UNKNOWN", "eMAID", "CERT-UNKNOWN"))
                .thenReturn(new SessionServiceClient.AuthorizationResult(false, "Invalid", "Authorization rejected", "ContractCancelled"));
        JsonNode request = objectMapper.readTree("""
                {
                  "vendorId": "org.openchargealliance.iso15118pnc",
                  "messageId": "Authorize",
                  "data": "{\\\"idToken\\\":{\\\"idToken\\\":\\\"US-EHB-UNKNOWN\\\"},\\\"certificate\\\":\\\"CERT-UNKNOWN\\\"}"
                }
                """);

        JsonNode response = handler.handle("EH-US-CHG-0003", request);
        JsonNode wrappedResponse = objectMapper.readTree(response.path("data").asText());

        assertEquals("Accepted", response.path("status").asText());
        assertEquals("Invalid", wrappedResponse.path("idTokenInfo").path("status").asText());
        assertEquals("ContractCancelled", wrappedResponse.path("certificateStatus").asText());
    }
}
