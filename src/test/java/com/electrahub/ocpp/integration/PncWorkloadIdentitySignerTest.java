package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PncWorkloadIdentitySignerTest {
    private static final String SECRET = "stage-14-test-shared-secret";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void signsTheShortLivedTenantScopedProtocolAdapterIdentity() throws Exception {
        UUID workload = UUID.fromString("9e55a5b5-18f8-5adf-a389-e6cba7fa6177");
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
        PncWorkloadIdentitySigner signer = new PncWorkloadIdentitySigner(json, SECRET, workload, clock);

        PncWorkloadIdentitySigner.IdentityHeaders headers = signer.sign("electrahub");
        JsonNode payload = json.readTree(Base64.getUrlDecoder().decode(headers.payload()));

        assertEquals(1, payload.path("version").asInt());
        assertEquals(workload.toString(), payload.path("userId").asText());
        assertEquals("electrahub", payload.path("tenantId").asText());
        assertEquals("PNC_PROTOCOL_ADAPTER", payload.path("roles").get(0).asText());
        assertEquals(clock.millis() + 30_000, payload.path("expiresAt").asLong());
        assertEquals(signature(headers.payload()), headers.signature());
        assertEquals("electrahub", headers.tenantId());
    }

    @Test
    void failsClosedForMissingSecretOrInvalidTenant() {
        UUID workload = UUID.randomUUID();
        assertThrows(IllegalStateException.class,
                () -> new PncWorkloadIdentitySigner(json, " ", workload, Clock.systemUTC()));
        PncWorkloadIdentitySigner signer =
                new PncWorkloadIdentitySigner(json, SECRET, workload, Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> signer.sign("Tenant With Spaces"));
    }

    private static String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
    }
}
