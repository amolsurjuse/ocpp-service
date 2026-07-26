package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppAuthorizationGrantService;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StartTransactionHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final OcppAuthorizationGrantService authorizationGrants = mock(OcppAuthorizationGrantService.class);
    private final StartTransactionHandler handler = new StartTransactionHandler(
            sessionServiceClient,
            authorizationGrants,
            new OcppTelemetryDispatcher(Runnable::run, new SimpleMeterRegistry())
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queuesAnAuthorizedRfidStartWithoutBlockingTheOcppResponse() throws Exception {
        when(authorizationGrants.consumeForStart("EH-US-CHG-0001", 1, "RFID-APPROVED", 810001)).thenReturn(true);
        when(authorizationGrants.correlationForStart("EH-US-CHG-0001", "RFID-APPROVED"))
                .thenReturn("4dd9ed1d-3d0c-4470-aa12-a78a9be56339");
        JsonNode payload = objectMapper.readTree("""
                {
                  "connectorId": 1,
                  "idTag": "RFID-APPROVED",
                  "meterStart": 1000,
                  "timestamp": "2026-07-25T18:45:00Z",
                  "transactionId": 810001
                }
                """);

        JsonNode response = handler.handle("EH-US-CHG-0001", payload);

        assertThat(response.path("idTagInfo").path("status").asText()).isEqualTo("Accepted");
        assertThat(response.path("transactionId").asInt()).isEqualTo(810001);
        verify(sessionServiceClient).onStartTransaction(
                "EH-US-CHG-0001", 1, "RFID-APPROVED", null, 1000,
                "2026-07-25T18:45:00Z", 810001, "4dd9ed1d-3d0c-4470-aa12-a78a9be56339"
        );
    }

    @Test
    void rejectsAnUnknownRfidBeforeCreatingAnyChargingSession() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "connectorId": 1,
                  "idTag": "RFID-UNKNOWN",
                  "meterStart": 1000,
                  "transactionId": 810002
                }
                """);

        JsonNode response = handler.handle("EH-US-CHG-0001", payload);

        assertThat(response.path("idTagInfo").path("status").asText()).isEqualTo("Invalid");
        assertThat(response.path("transactionId").asInt()).isZero();
        verify(authorizationGrants).consumeForStart("EH-US-CHG-0001", 1, "RFID-UNKNOWN", 810002);
        verifyNoInteractions(sessionServiceClient);
    }

    @Test
    void keepsCardPresentVerificationSynchronous() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "connectorId": 1,
                  "idTag": "CP:payment-token",
                  "meterStart": 1000,
                  "transactionId": 810003
                }
                """);

        JsonNode response = handler.handle("EH-US-CHG-0001", payload);

        assertThat(response.path("idTagInfo").path("status").asText()).isEqualTo("Accepted");
        verify(sessionServiceClient).onStartTransaction(
                "EH-US-CHG-0001", 1, "CP:payment-token", null, 1000, "", 810003, null
        );
        verify(authorizationGrants).consumeForStart("EH-US-CHG-0001", 1, "CP:payment-token", 810003);
    }
}
