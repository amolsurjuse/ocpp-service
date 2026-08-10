package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PncCertificateInstallationClientTest {
    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsTheExactInternalContractWithSignedTenantIdentity() throws Exception {
        AtomicReference<JsonNode> body = new AtomicReference<>();
        AtomicReference<String> context = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        startServer(exchange -> {
            body.set(json.readTree(exchange.getRequestBody()));
            context.set(exchange.getRequestHeaders().getFirst(PncWorkloadIdentitySigner.CONTEXT_HEADER));
            signature.set(exchange.getRequestHeaders().getFirst(PncWorkloadIdentitySigner.SIGNATURE_HEADER));
            assertEquals("electrahub", exchange.getRequestHeaders().getFirst(PncWorkloadIdentitySigner.TENANT_HEADER));
            respond(exchange, 200, "{\"status\":\"Accepted\",\"exiResponse\":\"BAUG\",\"exchangeId\":\"one\"}");
        });

        PncCertificateInstallationClient.Result result = client().install("electrahub", request());

        assertTrue(result.accepted());
        assertEquals("BAUG", result.exiResponse());
        assertEquals("EH-US-CHG-0003", body.get().path("chargingStationId").asText());
        assertEquals("message-42", body.get().path("ocppMessageId").asText());
        assertEquals("Install", body.get().path("action").asText());
        assertEquals(Get15118Schema.VALUE, body.get().path("schemaVersion").asText());
        assertEquals("AQID", body.get().path("exiRequestBase64").asText());
        assertNotNull(context.get());
        assertNotNull(signature.get());
    }

    @Test
    void failsClosedForMalformedProviderResponse() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"status\":\"Accepted\",\"exiResponse\":\"not base64\"}"));

        PncCertificateInstallationClient.Result result = client().install("electrahub", request());

        assertFalse(result.accepted());
        assertEquals(null, result.exiResponse());
    }

    private PncCertificateInstallationClient client() {
        PncWorkloadIdentitySigner signer = new PncWorkloadIdentitySigner(json, "stage-14-test-shared-secret",
                UUID.fromString("9e55a5b5-18f8-5adf-a389-e6cba7fa6177"), Clock.systemUTC());
        return new PncCertificateInstallationClient(RestClient.builder(), signer, new SimpleMeterRegistry(),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private PncCertificateInstallationClient.Request request() {
        return new PncCertificateInstallationClient.Request(
                "EH-US-CHG-0003", "message-42", "Install", Get15118Schema.VALUE, "AQID");
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/iso15118/certificate-installations", exchange -> {
            try { handler.handle(exchange); } finally { exchange.close(); }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
    }

    private static final class Get15118Schema {
        private static final String VALUE = "urn:iso:15118:2:2013:MsgDef";
    }

    @FunctionalInterface
    private interface ExchangeHandler { void handle(HttpExchange exchange) throws IOException; }
}
