package com.electrahub.ocpp.handler;

import com.electrahub.ocpp.integration.SessionServiceClient;
import com.electrahub.ocpp.service.OcppTelemetryDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MeterValuesHandlerTest {
    private final SessionServiceClient sessionServiceClient = mock(SessionServiceClient.class);
    private final MeterValuesHandler handler = new MeterValuesHandler(
            sessionServiceClient,
            new OcppTelemetryDispatcher(Runnable::run, new SimpleMeterRegistry())
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forwardsOnlyMeasurandsReportedByTheCharger() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "connectorId": 1,
                  "transactionId": 320101,
                  "meterValue": [{
                    "timestamp": "2026-07-15T21:18:50Z",
                    "sampledValue": [
                      {"value":"1200122","measurand":"Energy.Active.Import.Register","unit":"Wh"},
                      {"value":"44000","measurand":"Power.Active.Import","unit":"W"},
                      {"value":"42","measurand":"SoC","unit":"Percent"}
                    ]
                  }]
                }
                """);

        handler.handle("EH-US-CHG-0201", payload);

        verify(sessionServiceClient).onMeterValues(
                "EH-US-CHG-0201",
                320101,
                1,
                "2026-07-15T21:18:50Z",
                new BigDecimal("1200122"),
                new BigDecimal("44000"),
                new BigDecimal("42")
        );
    }

    @Test
    void leavesMissingPowerAndStateOfChargeAbsent() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "connectorId": 1,
                  "transactionId": 320101,
                  "meterValue": [{
                    "timestamp": "2026-07-15T21:18:50Z",
                    "sampledValue": [
                      {"value":"1200122","measurand":"Energy.Active.Import.Register","unit":"Wh"}
                    ]
                  }]
                }
                """);

        handler.handle("EH-US-CHG-0201", payload);

        verify(sessionServiceClient).onMeterValues(
                "EH-US-CHG-0201",
                320101,
                1,
                "2026-07-15T21:18:50Z",
                new BigDecimal("1200122"),
                null,
                null
        );
    }
}
