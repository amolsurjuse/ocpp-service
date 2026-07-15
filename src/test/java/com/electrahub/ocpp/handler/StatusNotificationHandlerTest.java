package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.integration.StationServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StatusNotificationHandlerTest {
    private final StationServiceClient stationServiceClient = mock(StationServiceClient.class);
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final StatusNotificationHandler handler = new StatusNotificationHandler(stationServiceClient, sessionServiceClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void endSessionInfoMarksSuspendedConnectorAsAwaitingUnplug() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "connectorId": 1,
                  "status": "SuspendedEV",
                  "errorCode": "NoError",
                  "timestamp": "2026-07-13T20:15:25Z",
                  "transactionId": 269349,
                  "info": "EndSessionRequested"
                }
                """);

        handler.handle("EH-US-CHG-0001", payload);

        verify(stationServiceClient).updateConnectorStatus("EH-US-CHG-0001", 1, "SuspendedEV");
        verify(sessionServiceClient).onStatusNotification(
                "EH-US-CHG-0001",
                1,
                "SuspendedEV",
                "NoError",
                "2026-07-13T20:15:25Z",
                269349,
                true
        );
    }
}
