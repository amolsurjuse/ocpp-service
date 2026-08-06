package com.electrahub.ocpp.websocket;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Rejects new charger sockets after Kubernetes has started draining this pod. */
@Component
public class OcppDrainHandshakeInterceptor implements HandshakeInterceptor {

    private final ConnectionManager connections;

    public OcppDrainHandshakeInterceptor(ConnectionManager connections) {
        this.connections = connections;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (connections.isAcceptingConnections()) {
            return true;
        }
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // No handshake state is retained.
    }
}
