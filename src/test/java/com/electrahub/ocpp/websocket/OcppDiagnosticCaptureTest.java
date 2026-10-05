package com.electrahub.ocpp.websocket;

import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import java.net.URI;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcppDiagnosticCaptureTest {
    private final ConnectionManager connections = mock(ConnectionManager.class);
    private final OcppMessageRouter router = mock(OcppMessageRouter.class);
    private final OcppMessageLogService journal = mock(OcppMessageLogService.class);
    private final OcppInboundCallDispatcher dispatcher = mock(OcppInboundCallDispatcher.class);
    private final OcppWebSocketHandler handler = new OcppWebSocketHandler(connections, router,
            mock(OcppConnectionRepository.class), journal, mock(ChargePointAvailabilityService.class), dispatcher);
    private final WebSocketSession socket = mock(WebSocketSession.class);

    @Test
    void recordsMeterExchangeAfterProcessingWithoutDelayingResponseBehindJournalFailure() throws Exception {
        when(socket.getUri()).thenReturn(URI.create("ws://localhost/ocpp/CP-1"));
        when(dispatcher.dispatch(anyString(), any(), any())).thenAnswer(call -> {
            call.getArgument(1, Runnable.class).run();
            return true;
        });
        var response = OcppJsonRpcMessage.createCallResult("m-1", new ObjectMapper().createObjectNode());
        when(router.routeMessage(eq("CP-1"), any())).thenReturn(response);
        doThrow(new IllegalStateException("queue full")).when(journal)
                .recordExchange(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        handler.handleTextMessage(socket, new TextMessage("[2,\"m-1\",\"MeterValues\",{\"transactionId\":42,\"connectorId\":1}]"));
        var order = inOrder(connections, journal);
        order.verify(connections).sendMessage(eq(socket), eq(response.toJson()));
        order.verify(journal).recordExchange(eq("CP-1"), eq("m-1"), eq("INBOUND"), eq("MeterValues"),
                any(), any(), eq("RESPONDED"), isNull(), any(), any());
        verify(journal, times(1)).recordExchange(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectedDispatchIsRecordedOnceAndDoesNotReachTheHandler() throws Exception {
        when(socket.getUri()).thenReturn(URI.create("ws://localhost/ocpp/CP-1"));
        when(socket.isOpen()).thenReturn(true);
        when(dispatcher.dispatch(anyString(), any(), any())).thenReturn(false);
        handler.handleTextMessage(socket, new TextMessage("[2,\"m-1\",\"MeterValues\",{\"transactionId\":42}]"));
        verify(journal).recordExchange(eq("CP-1"), eq("m-1"), eq("INBOUND"), eq("MeterValues"),
                any(), isNull(), eq("QUEUE_REJECTED"), isNull(), any(), isNull());
        verifyNoInteractions(router);
    }
}
