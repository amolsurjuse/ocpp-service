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

    @Test
    void sendsPlugAndChargeTokenTypeAndCertificateToSessionService() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"authorized\":true,\"status\":\"Accepted\",\"reason\":null,\"certificateStatus\":\"Accepted\"}");
        });

        SessionServiceClient.AuthorizationResult result = client().authorize(
                "US-EHB-C12345678",
                "eMAID",
                "CERT-EHB-001"
        );

        assertTrue(result.authorized());
        assertEquals("Accepted", result.certificateStatus());
        assertTrue(requestBody.get().contains("\"idTokenType\":\"eMAID\""));
        assertTrue(requestBody.get().contains("\"contractCertificate\":\"CERT-EHB-001\""));
    }

    @Test
    void sendsNativeOcpp201TransactionIdWithoutReplacingCompatibilityId() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer("/api/v1/sessions/ocpp/start-transaction", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 204, "");
        });

        client().onStartTransaction(
                "SC-CANARY-201-CP1", 1, "RFID-201", "ISO14443", 1000,
                "2026-08-07T12:00:00Z", 253569, "tx-ocpp201-native-001",
                "correlation-201", "message-201"
        );

        assertTrue(requestBody.get().contains("\"transactionId\":253569"));
        assertTrue(requestBody.get().contains("\"nativeTransactionId\":\"tx-ocpp201-native-001\""));
    }

    private SessionServiceClient client() {
        return new SessionServiceClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "integration-test-token"
        );
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        startServer("/api/v1/sessions/authorize", handler);
    }

    private void startServer(String path, ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
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
