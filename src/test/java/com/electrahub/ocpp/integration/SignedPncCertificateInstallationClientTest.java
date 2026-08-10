package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedPncCertificateInstallationClientTest {
    private static final String SECRET = "stage7-pnc-identity-secret";
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsShortLivedSignedProtocolAdapterIdentityAndExactMessageId() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/iso15118/certificate-installations", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String context = exchange.getRequestHeaders().getFirst("X-ElectraHub-Identity-Context");
            assertEquals("electrahub", exchange.getRequestHeaders().getFirst("X-ElectraHub-Tenant-Id"));
            assertEquals(sign(context), exchange.getRequestHeaders().getFirst("X-ElectraHub-Identity-Context-Signature"));
            JsonNode identity = json.readTree(Base64.getUrlDecoder().decode(context));
            assertEquals("electrahub", identity.path("tenantId").asText());
            assertEquals("PNC_PROTOCOL_ADAPTER", identity.path("roles").path(0).asText());
            assertTrue(identity.path("expiresAt").asLong() > System.currentTimeMillis());
            respond(exchange, "{\"status\":\"Failed\",\"exiResponse\":null,\"failureCategory\":\"PROVIDER_UNAVAILABLE\",\"exchangeId\":\"b73f2d5a-69b9-4ed1-a3f2-7bd2f95ad3e5\"}");
        });
        server.start();

        var client = new SignedPncCertificateInstallationClient(RestClient.builder(), json,
                "http://127.0.0.1:" + server.getAddress().getPort(), SECRET);
        var response = client.install(new PncCertificateInstallationClient.Request(
                "electrahub", "CP-201", "ocpp-message-exact", "Install",
                "urn:iso:15118:2:2013:MsgDef", "dGVzdA=="));

        assertEquals("Failed", response.status());
        assertTrue(body.get().contains("\"ocppMessageId\":\"ocpp-message-exact\""));
    }

    private static String sign(String context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(context.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws java.io.IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
