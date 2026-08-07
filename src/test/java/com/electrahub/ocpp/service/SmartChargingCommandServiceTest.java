package com.electrahub.ocpp.service;

import com.electrahub.ocpp.web.dto.SmartChargingCommandResponse;
import com.electrahub.ocpp.web.dto.SmartChargingLimitRequest;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SmartChargingCommandServiceTest {

    @Test
    void distinguishesAcceptedFromTransportSuccessAndUsesRegistryProtocol() {
        ObjectMapper objectMapper = new ObjectMapper();
        ConnectionManager connections = mock(ConnectionManager.class);
        RemoteCommandService commands = mock(RemoteCommandService.class);
        SmartChargingIdempotencyService idempotency = passthroughIdempotency();
        when(connections.getProtocol("CP-201")).thenReturn("OCPP201");
        ObjectNode response = objectMapper.createObjectNode().put("status", "Rejected");
        when(commands.sendCommand(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(response));
        SmartChargingCommandService service = new SmartChargingCommandService(
                connections, commands, new SmartChargingProfileMapper(objectMapper),
                idempotency, objectMapper, new SimpleMeterRegistry());

        SmartChargingCommandResponse result = service.setLimit("CP-201", request()).join();

        assertThat(result.protocol()).isEqualTo("OCPP201");
        assertThat(result.chargerStatus()).isEqualTo("Rejected");
        assertThat(result.accepted()).isFalse();
        verify(commands).sendCommand(
                eq("CP-201"), eq("SetChargingProfile"),
                org.mockito.ArgumentMatchers.argThat(payload -> payload.has("evseId") && payload.has("chargingProfile")));
    }

    @Test
    void completedReplayDoesNotRequireLiveConnectionLookup() {
        ObjectMapper objectMapper = new ObjectMapper();
        ConnectionManager connections = mock(ConnectionManager.class);
        RemoteCommandService commands = mock(RemoteCommandService.class);
        SmartChargingIdempotencyService idempotency = mock(SmartChargingIdempotencyService.class);
        SmartChargingCommandResponse replay = new SmartChargingCommandResponse(
                "OCPP16J", "SetChargingProfile", 100, "Accepted", true, true, null);
        when(idempotency.execute(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(replay));
        SmartChargingCommandService service = new SmartChargingCommandService(
                connections, commands, new SmartChargingProfileMapper(objectMapper),
                idempotency, objectMapper, new SimpleMeterRegistry());

        assertThat(service.setLimit("CP-OFFLINE", request()).join().replayed()).isTrue();
        verify(connections, never()).getProtocol(anyString());
        verify(commands, never()).sendCommand(anyString(), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    private SmartChargingIdempotencyService passthroughIdempotency() {
        SmartChargingIdempotencyService service = mock(SmartChargingIdempotencyService.class);
        when(service.execute(anyString(), anyString(), any())).thenAnswer(invocation ->
                ((Supplier<CompletableFuture<SmartChargingCommandResponse>>) invocation.getArgument(2)).get());
        return service;
    }

    private SmartChargingLimitRequest request() {
        return new SmartChargingLimitRequest(
                "decision:connector:1", 1, "tx-1", 100, 200, 10,
                new BigDecimal("42.5"), Instant.parse("2026-08-07T12:00:00Z"),
                Instant.parse("2026-08-07T12:02:00Z"), SmartChargingLimitRequest.Purpose.SESSION_LIMIT);
    }
}
