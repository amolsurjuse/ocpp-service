package com.electrahub.ocpp.integration;

import com.electrahub.ocpp.config.InternalServiceTokenFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void authorizesWhenSessionServiceAcceptsTheRfid() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("integration-test-token", exchange.getRequestHeaders().getFirst(InternalServiceTokenFilter.HEADER_NAME));
            respond(exchange, 200, "{\"authorized\":true,\"status\":\"Accepted\",\"reason\":null}");
        });

        SessionServiceClient client = client();

        assertTrue(client.authorize("RFID-KNOWN-001"));
        assertTrue(requestBody.get().contains("RFID-KNOWN-001"));
    }

    @Test
    void rejectsWhenSessionServiceRejectsTheRfid() throws IOException {
        startServer(exchange -> respond(
                exchange,
                200,
                "{\"authorized\":false,\"status\":\"INVALID\",\"reason\":\"Authorization rejected\"}"
        ));

        assertFalse(client().authorize("RFID-UNKNOWN-001"));
    }

    private SessionServiceClient client() {
        return new SessionServiceClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "integration-test-token"
        );
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/sessions/authorize", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
