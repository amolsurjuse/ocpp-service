package com.electrahub.ocpp.service;

import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RemoteCommandDiagnosticJournalTest {
    private final ConnectionManager connections = mock(ConnectionManager.class);
    private final OcppMessageLogService journal = mock(OcppMessageLogService.class);
    private final RemoteCommandService commands = new RemoteCommandService(connections,
            mock(OcppAuthorizationGrantService.class), mock(RemoteStartCommandStore.class),
            mock(IdTagFingerprintService.class), new SimpleMeterRegistry(), mock(OcppClusterCommandRouter.class), 30);

    @Test
    void recordsRejectionWithoutChangingTheCommandResponse() throws Exception {
        commands.setDiagnosticJournal(journal);
        when(connections.isLocalConnectionOwner("CP-1")).thenReturn(true);
        var request = new ObjectMapper().readTree("{\"connectorId\":1}");
        var response = new ObjectMapper().readTree("{\"status\":\"Rejected\"}");
        var future = commands.sendCommandLocally("CP-1", "RemoteStartTransaction", request, "m-1");
        commands.resolvePendingResponse("m-1", response);
        assertThat(future.join()).isEqualTo(response);
        verify(journal).recordExchange(eq("CP-1"), eq("m-1"), eq("OUTBOUND"), eq("RemoteStartTransaction"),
                eq(request), eq(response), eq("success"), isNull(), any(), any());
    }

    @Test
    void recordsCallErrorCodeWithoutErrorDescription() throws Exception {
        commands.setDiagnosticJournal(journal);
        when(connections.isLocalConnectionOwner("CP-1")).thenReturn(true);
        var request = new ObjectMapper().readTree("{\"transactionId\":42}");
        var future = commands.sendCommandLocally("CP-1", "RemoteStopTransaction", request, "m-1");
        commands.rejectPendingResponse("m-1", "NotSupported", "private device information");
        assertThat(future).isCompletedExceptionally();
        verify(journal).recordExchange(eq("CP-1"), eq("m-1"), eq("OUTBOUND"), eq("RemoteStopTransaction"),
                eq(request), isNull(), eq("CALL_ERROR"), eq("NotSupported"), any(), any());
    }

    @Test
    void journalQueueFailureDoesNotFailSuccessfulChargingCommand() throws Exception {
        commands.setDiagnosticJournal(journal);
        when(connections.isLocalConnectionOwner("CP-1")).thenReturn(true);
        doThrow(new IllegalStateException("queue full")).when(journal).recordExchange(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        var response = new ObjectMapper().readTree("{\"status\":\"Accepted\"}");
        var future = commands.sendCommandLocally("CP-1", "RemoteStartTransaction", response, "m-1");
        commands.resolvePendingResponse("m-1", response);
        assertThat(future.join()).isEqualTo(response);
    }
}
