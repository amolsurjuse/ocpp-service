package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionEventHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final TransactionEventHandler handler = new TransactionEventHandler(sessionServiceClient);
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
                0,
                "2026-07-07T03:11:23Z",
                "RemoteStop"
        );
    }
}
