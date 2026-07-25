package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppAuthorizationGrantService;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionEventHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final OcppAuthorizationGrantService authorizationGrants = mock(OcppAuthorizationGrantService.class);
    private final TransactionEventHandler handler = new TransactionEventHandler(
            sessionServiceClient,
            authorizationGrants,
            new OcppTelemetryDispatcher(Runnable::run, new SimpleMeterRegistry())
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void endedEventUsesStoppedReasonForSessionStop() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "eventType": "Ended",
                  "timestamp": "2026-07-07T03:11:23Z",
                  "triggerReason": "EVCommunicationLost",
                  "evse": { "id": 1 },
                  "transactionInfo": {
                    "transactionId": 915056,
                    "stoppedReason": "EVDisconnected"
                  },
                  "meterValue": [
                    {
                      "timestamp": "2026-07-07T03:11:23Z",
                      "sampledValue": [
                        {
                          "value": 1201500,
                          "measurand": "Energy.Active.Import.Register",
                          "unitOfMeasure": { "unit": "Wh" }
                        }
                      ]
                    }
                  ]
                }
                """);

        handler.handle("EH-US-CHG-0056", payload);

        verify(sessionServiceClient).onStopTransaction(
                915056,
                "EH-US-CHG-0056",
                1,
                1201500,
                "2026-07-07T03:11:23Z",
                "EVDisconnected"
        );
    }

    @Test
    void endedEventFallsBackToTriggerReasonWhenStoppedReasonMissing() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "eventType": "Ended",
                  "timestamp": "2026-07-07T03:11:23Z",
                  "triggerReason": "RemoteStop",
                  "evse": { "id": 1 },
                  "transactionInfo": {
                    "transactionId": 915057
                  }
                }
                """);

        handler.handle("EH-US-CHG-0057", payload);

        verify(sessionServiceClient).onStopTransaction(
                915057,
                "EH-US-CHG-0057",
                1,
                0,
                "2026-07-07T03:11:23Z",
                "RemoteStop"
        );
    }

    @Test
    void chargingStateChangePropagatesAwaitingUnplugIntent() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "eventType": "Updated",
                  "timestamp": "2026-07-13T20:15:25Z",
                  "triggerReason": "ChargingStateChanged",
                  "evse": { "id": 1 },
                  "transactionInfo": {
                    "transactionId": 269349,
                    "chargingState": "SuspendedEV"
                  },
                  "customData": {
                    "vendorId": "ElectraHub",
                    "endSessionRequested": true,
                    "errorCode": "NoError"
                  }
                }
                """);

        handler.handle("EH-US-CHG-0001", payload);

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

    @Test
    void periodicEnergyTelemetryDoesNotInventMissingPowerOrStateOfCharge() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "eventType": "Updated",
                  "timestamp": "2026-07-15T21:18:50Z",
                  "triggerReason": "MeterValuePeriodic",
                  "evse": { "id": 1 },
                  "transactionInfo": { "transactionId": 320101 },
                  "meterValue": [{
                    "timestamp": "2026-07-15T21:18:50Z",
                    "sampledValue": [{
                      "value": 1200122,
                      "measurand": "Energy.Active.Import.Register",
                      "unitOfMeasure": { "unit": "Wh" }
                    }]
                  }]
                }
                """);

        handler.handle("EH-US-CHG-0201", payload);

        verify(sessionServiceClient).onMeterValues(
                "EH-US-CHG-0201",
                320101,
                1,
                "2026-07-15T21:18:50Z",
                new java.math.BigDecimal("1200122"),
                null,
                null
        );
    }
}
