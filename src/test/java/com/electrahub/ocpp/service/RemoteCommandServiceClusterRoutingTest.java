package com.electrahub.ocpp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RemoteCommandServiceClusterRoutingTest {

    @Test
    void forwardsToRemoteOwnerInsteadOfReportingDisconnected() {
        ConnectionManager connections = mock(ConnectionManager.class);
        OcppClusterCommandRouter cluster = mock(OcppClusterCommandRouter.class);
        ObjectMapper mapper = new ObjectMapper();
        when(connections.connectionOwnership("EH-001"))
                .thenReturn(ConnectionManager.ConnectionOwnership.REMOTE_OWNER);
        when(connections.getOwnerNodeId("EH-001")).thenReturn(Optional.of("node-b"));
        when(cluster.isEnabled()).thenReturn(true);
        when(cluster.route(eq("node-b"), eq("EH-001"), eq("Reset"), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(
                        mapper.createObjectNode().put("status", "Accepted")));
        RemoteCommandService service = new RemoteCommandService(
                connections,
                mock(OcppAuthorizationGrantService.class),
                mock(RemoteStartCommandStore.class),
                mock(IdTagFingerprintService.class),
                new SimpleMeterRegistry(),
                cluster,
                30);

        var result = service.reset("EH-001", "Soft").join();

        assertThat(result.path("status").asText()).isEqualTo("Accepted");
        verify(cluster).route(eq("node-b"), eq("EH-001"), eq("Reset"), any(), anyString());
    }
}
