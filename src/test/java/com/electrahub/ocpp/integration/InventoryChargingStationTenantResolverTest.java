package com.electrahub.ocpp.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryChargingStationTenantResolverTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolvesCanonicalInventoryTenantWithWorkloadToken() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        start(exchange -> {
            assertEquals("internal-test-token", exchange.getRequestHeaders().getFirst("X-ElectraHub-Internal-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"chargerId\":\"CP-201-A\",\"tenantId\":\"tenant-a\"}");
        });

        String tenant = resolver().resolveTenant(" cp-201-a ");

        assertEquals("tenant-a", tenant);
        assertTrue(body.get().contains("\"chargerId\":\"CP-201-A\""));
    }

    @Test
    void mismatchedInventoryIdentityFailsClosed() throws Exception {
        start(exchange -> respond(exchange, 200,
                "{\"chargerId\":\"DIFFERENT\",\"tenantId\":\"tenant-a\"}"));
        assertThrows(IllegalStateException.class, () -> resolver().resolveTenant("CP-201-A"));
    }

    private InventoryChargingStationTenantResolver resolver() {
        return new InventoryChargingStationTenantResolver(RestClient.builder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "internal-test-token");
    }

    private void start(Handler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/ownership/resolve", exchange -> {
            try { handler.handle(exchange); } finally { exchange.close(); }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private interface Handler { void handle(HttpExchange exchange) throws java.io.IOException; }
}
