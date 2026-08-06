package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppRemoteStartCommand;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandOutcome;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandState;
import com.electrahub.ocpp.exception.ChargePointNotConnectedException;
import com.electrahub.ocpp.exception.ChargePointRouteUnavailableException;
import com.electrahub.ocpp.exception.OcppProtocolException;
import com.electrahub.ocpp.web.dto.RemoteStartCommandStatusResponse;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.electrahub.ocpp.websocket.OcppJsonRpcMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteCommandIdempotencyTest {

    private final ConnectionManager connectionManager = mock(ConnectionManager.class);
    private final OcppAuthorizationGrantService authorizationGrants = mock(OcppAuthorizationGrantService.class);
    private final RemoteStartCommandStore commandStore = mock(RemoteStartCommandStore.class);
    private final IdTagFingerprintService idTagFingerprints = new IdTagFingerprintService(
            "test-only-id-tag-fingerprint-key-32-bytes");
    private final RemoteCommandService service = new RemoteCommandService(
            connectionManager,
            authorizationGrants,
            commandStore,
            idTagFingerprints,
            new SimpleMeterRegistry(),
            mock(OcppClusterCommandRouter.class),
            30
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExecutorService executor;

    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicatePostsSendOneOcpp201CommandAndReplayPending() throws Exception {
        String commandKey = "remote-start:13d43a24-ecf9-4454-a048-faa32791dd2a";
        OcppRemoteStartCommand pending = command(
                commandKey,
                RemoteStartCommandState.PENDING,
                null,
                null,
                null
        );
        OcppRemoteStartCommand accepted = command(
                commandKey,
                RemoteStartCommandState.TERMINAL,
                RemoteStartCommandOutcome.ACCEPTED,
                "{\"status\":\"Accepted\"}",
                null
        );

        when(connectionManager.getProtocol("CP-201")).thenReturn("OCPP201");
        when(connectionManager.connectionOwnership("CP-201"))
                .thenReturn(ConnectionManager.ConnectionOwnership.LOCAL_OWNER);
        when(connectionManager.isLocalConnectionOwner("CP-201")).thenReturn(true);
        when(connectionManager.isConnected("CP-201")).thenReturn(true);
        when(authorizationGrants.grantRemoteStart("CP-201", 1, "RFID-1", "13d43a24-ecf9-4454-a048-faa32791dd2a"))
                .thenReturn(true);
        when(commandStore.claim(any(RemoteStartCommandStore.CommandSpec.class), any(Duration.class)))
                .thenReturn(
                        new RemoteStartCommandStore.Claim(true, pending),
                        new RemoteStartCommandStore.Claim(false, pending)
                );
        when(commandStore.complete(any(), any(), any(), any(), any())).thenReturn(accepted);

        executor = Executors.newFixedThreadPool(2);
        Future<CompletableFuture<JsonNode>> first = executor.submit(() -> service.remoteStartTransaction(
                "CP-201", "RFID-1", 1,
                "13d43a24-ecf9-4454-a048-faa32791dd2a", commandKey));
        Future<CompletableFuture<JsonNode>> second = executor.submit(() -> service.remoteStartTransaction(
                "CP-201", "RFID-1", 1,
                "13d43a24-ecf9-4454-a048-faa32791dd2a", commandKey));

        List<CompletableFuture<JsonNode>> results = List.of(first.get(), second.get());
        CompletableFuture<JsonNode> replay = results.stream().filter(CompletableFuture::isDone).findFirst().orElseThrow();
        CompletableFuture<JsonNode> original = results.stream().filter(result -> !result.isDone()).findFirst().orElseThrow();

        assertThat(replay.get().path("status").asText()).isEqualTo("Pending");
        assertThat(replay.get().path("commandState").asText()).isEqualTo("PENDING");
        assertThat(replay.get().path("replayed").asBoolean()).isTrue();

        ArgumentCaptor<String> sentPayload = ArgumentCaptor.forClass(String.class);
        verify(connectionManager).sendMessage(org.mockito.ArgumentMatchers.eq("CP-201"), sentPayload.capture());
        verify(authorizationGrants).grantRemoteStart(
                "CP-201", 1, "RFID-1", "13d43a24-ecf9-4454-a048-faa32791dd2a");

        OcppJsonRpcMessage sent = OcppJsonRpcMessage.parse(sentPayload.getValue());
        assertThat(sent.getAction()).isEqualTo("RequestStartTransaction");
        assertThat(sent.getMessageId()).isEqualTo("message-1");
        assertThat(sent.getPayload().path("remoteStartId").asInt())
                .isEqualTo(RemoteCommandService.deterministicRemoteStartId(commandKey));

        JsonNode chargerAccepted = objectMapper.createObjectNode().put("status", "Accepted");
        service.resolvePendingResponse("message-1", chargerAccepted);

        JsonNode acceptedResponse = original.get();
        assertThat(acceptedResponse.path("status").asText()).isEqualTo("Accepted");
        assertThat(acceptedResponse.path("commandOutcome").asText()).isEqualTo("ACCEPTED");
        assertThat(acceptedResponse.path("replayed").asBoolean()).isFalse();
    }

    @Test
    void callersWithoutCommandKeyRetainLegacyNonDurablePath() throws Exception {
        when(connectionManager.connectionOwnership("CP-16"))
                .thenReturn(ConnectionManager.ConnectionOwnership.LOCAL_OWNER);
        when(connectionManager.isLocalConnectionOwner("CP-16")).thenReturn(true);
        when(connectionManager.getProtocol("CP-16")).thenReturn("OCPP16J");
        when(authorizationGrants.grantRemoteStart("CP-16", 1, "RFID-1", "session-legacy"))
                .thenReturn(true);

        service.remoteStartTransaction("CP-16", "RFID-1", 1, "session-legacy", null);

        verify(commandStore, never()).claim(any(), any());
        verify(connectionManager).sendMessage(org.mockito.ArgumentMatchers.eq("CP-16"), anyString());
    }

    @Test
    void uncertainWebSocketWriteReturnsUnknownRatherThanAccepted() throws Exception {
        String commandKey = "remote-start:write-uncertain";
        OcppRemoteStartCommand pending = command(
                commandKey,
                RemoteStartCommandState.PENDING,
                null,
                null,
                null
        );
        OcppRemoteStartCommand unknown = command(
                commandKey,
                RemoteStartCommandState.UNKNOWN,
                null,
                null,
                "IOException: uncertain write"
        );
        when(connectionManager.getProtocol("CP-201")).thenReturn("OCPP201");
        when(connectionManager.connectionOwnership("CP-201"))
                .thenReturn(ConnectionManager.ConnectionOwnership.LOCAL_OWNER);
        when(connectionManager.isConnected("CP-201")).thenReturn(true);
        when(authorizationGrants.grantRemoteStart(
                "CP-201", 1, "RFID-1", "13d43a24-ecf9-4454-a048-faa32791dd2a"))
                .thenReturn(true);
        when(commandStore.claim(any(RemoteStartCommandStore.CommandSpec.class), any(Duration.class)))
                .thenReturn(new RemoteStartCommandStore.Claim(true, pending));
        when(commandStore.complete(any(), any(), any(), any(), any())).thenReturn(unknown);
        doThrow(new java.io.IOException("uncertain write"))
                .when(connectionManager).sendMessage(
                        org.mockito.ArgumentMatchers.eq("CP-201"),
                        anyString()
                );

        JsonNode response = service.remoteStartTransaction(
                "CP-201",
                "RFID-1",
                1,
                "13d43a24-ecf9-4454-a048-faa32791dd2a",
                commandKey
        ).get();

        assertThat(response.path("status").asText()).isEqualTo("Unknown");
        assertThat(response.path("commandState").asText()).isEqualTo("UNKNOWN");
        assertThat(response.path("commandOutcome").isNull()).isTrue();
        assertThat(response.path("replayed").asBoolean()).isFalse();
    }

    @Test
    void unknownStatusNeverClaimsAnAcceptedOutcome() {
        String commandKey = "remote-start:unknown-session";
        OcppRemoteStartCommand unknown = command(
                commandKey,
                RemoteStartCommandState.UNKNOWN,
                null,
                null,
                RemoteStartCommandStore.EXPIRED_PENDING_REASON
        );
        when(commandStore.find(commandKey)).thenReturn(Optional.of(unknown));

        RemoteStartCommandStatusResponse status = service.remoteStartStatus(commandKey).orElseThrow();

        assertThat(status.state()).isEqualTo("UNKNOWN");
        assertThat(status.outcome()).isNull();
        assertThat(status.failureReason()).isEqualTo(RemoteStartCommandStore.EXPIRED_PENDING_REASON);
    }

    @Test
    void nonOwningReplicaDoesNotCreateADurableClaim() {
        when(connectionManager.connectionOwnership("CP-201"))
                .thenReturn(ConnectionManager.ConnectionOwnership.REMOTE_OWNER);

        assertThatThrownBy(() -> service.remoteStartTransaction(
                "CP-201",
                "RFID-1",
                1,
                "session-1",
                "remote-start:session-1"
        )).isInstanceOf(ChargePointRouteUnavailableException.class);

        verify(commandStore, never()).claim(any(), any());
        verify(connectionManager, never()).getProtocol(anyString());
        verify(authorizationGrants, never()).grantRemoteStart(anyString(), any(), anyString(), any());
    }

    @Test
    void genuinelyOfflineChargePointStillReturnsNotConnectedWithoutClaiming() {
        when(connectionManager.connectionOwnership("CP-OFFLINE"))
                .thenReturn(ConnectionManager.ConnectionOwnership.OFFLINE);

        assertThatThrownBy(() -> service.remoteStartTransaction(
                "CP-OFFLINE",
                "RFID-1",
                1,
                "session-offline",
                "remote-start:session-offline"
        )).isInstanceOf(ChargePointNotConnectedException.class);

        verify(commandStore, never()).claim(any(), any());
        verify(connectionManager, never()).getProtocol(anyString());
    }

    @Test
    void suppliedBlankCommandKeyIsRejectedInsteadOfUsingLegacyDispatch() throws Exception {
        assertThatThrownBy(() -> service.remoteStartTransaction(
                "CP-16",
                "RFID-1",
                1,
                "session-1",
                "   "
        )).isInstanceOf(OcppProtocolException.class)
                .hasMessageContaining("commandKey must be 1-200 characters");

        verify(commandStore, never()).claim(any(), any());
        verify(connectionManager, never()).sendMessage(anyString(), anyString());
    }

    @Test
    void deterministicRemoteStartIdIsStablePositiveAndKeySpecific() {
        int first = RemoteCommandService.deterministicRemoteStartId("remote-start:one");
        int replay = RemoteCommandService.deterministicRemoteStartId("remote-start:one");
        int other = RemoteCommandService.deterministicRemoteStartId("remote-start:two");

        assertThat(first).isPositive().isEqualTo(replay);
        assertThat(other).isPositive().isNotEqualTo(first);
    }

    private OcppRemoteStartCommand command(
            String commandKey,
            RemoteStartCommandState state,
            RemoteStartCommandOutcome outcome,
            String responsePayload,
            String failureReason
    ) {
        Instant now = Instant.now();
        return OcppRemoteStartCommand.builder()
                .id(java.util.UUID.randomUUID())
                .commandKey(commandKey)
                .correlationId("13d43a24-ecf9-4454-a048-faa32791dd2a")
                .chargePointId("CP-201")
                .idTagFingerprint(idTagFingerprints.fingerprint("RFID-1"))
                .connectorId(1)
                .protocol("OCPP201")
                .messageId("message-1")
                .remoteStartId(RemoteCommandService.deterministicRemoteStartId(commandKey))
                .state(state)
                .outcome(outcome)
                .responsePayload(responsePayload)
                .failureReason(failureReason)
                .createdAt(now.minusSeconds(1))
                .updatedAt(now)
                .deadlineAt(now.plusSeconds(30))
                .completedAt(state == RemoteStartCommandState.PENDING ? null : now)
                .build();
    }
}
