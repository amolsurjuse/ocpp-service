package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StopTransactionHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final StopTransactionHandler handler = new StopTransactionHandler(
            sessionServiceClient,
            new OcppTelemetryDispatcher(Runnable::run, new SimpleMeterRegistry())
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardsChargePointAndConnectorForFallbackResolution() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "transactionId": 915131,
                  "connectorId": 1,
                  "meterStop": 1201500,
                  "timestamp": "2026-07-08T22:45:00Z",
                  "reason": "EVDisconnected"
                }
                """);

        handler.handle("EH-US-CHG-0131", payload);

        verify(sessionServiceClient).onStopTransaction(
                915131,
                "EH-US-CHG-0131",
                1,
                1201500,
                "2026-07-08T22:45:00Z",
                "EVDisconnected"
        );
    }
}
