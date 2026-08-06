package com.electrahub.ocpp.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class OcppDrainHandshakeInterceptorTest {

    @Test
    void rejectsNewSocketsWhilePodIsDraining() {
        ConnectionManager connections = mock(ConnectionManager.class);
        when(connections.isAcceptingConnections()).thenReturn(false);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        OcppDrainHandshakeInterceptor interceptor = new OcppDrainHandshakeInterceptor(connections);

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class), response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void acceptsSocketsBeforeDrainStarts() {
        ConnectionManager connections = mock(ConnectionManager.class);
        when(connections.isAcceptingConnections()).thenReturn(true);
        OcppDrainHandshakeInterceptor interceptor = new OcppDrainHandshakeInterceptor(connections);

        boolean accepted = interceptor.beforeHandshake(
                mock(ServerHttpRequest.class), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isTrue();
    }
}
