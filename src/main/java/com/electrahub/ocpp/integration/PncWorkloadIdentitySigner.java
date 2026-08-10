package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
class PncWorkloadIdentitySigner {
    static final String CONTEXT_HEADER = "X-ElectraHub-Identity-Context";
    static final String SIGNATURE_HEADER = "X-ElectraHub-Identity-Context-Signature";
    static final String TENANT_HEADER = "X-ElectraHub-Tenant-Id";
    private static final long VALIDITY_MILLIS = 30_000;

    private final ObjectMapper json;
    private final byte[] secret;
    private final UUID workloadId;
    private final Clock clock;

    PncWorkloadIdentitySigner(
            ObjectMapper json,
            @Value("${app.pnc.identity-secret:${APP_INTERNAL_ACCESS_CONTEXT_SECRET:}}") String secret,
            @Value("${app.pnc.workload-id:${OCPP_PNC_WORKLOAD_ID:9e55a5b5-18f8-5adf-a389-e6cba7fa6177}}")
            UUID workloadId) {
        this(json, secret, workloadId, Clock.systemUTC());
    }

    PncWorkloadIdentitySigner(ObjectMapper json, String secret, UUID workloadId, Clock clock) {
        if (secret == null || secret.isBlank() || secret.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("APP_INTERNAL_ACCESS_CONTEXT_SECRET is required when ISO 15118 installation is enabled");
        }
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.workloadId = workloadId;
        this.clock = clock;
    }

    IdentityHeaders sign(String tenantId) {
        String tenant = requiredTenant(tenantId);
        try {
            byte[] body = json.writeValueAsBytes(Map.of(
                    "version", 1,
                    "userId", workloadId.toString(),
                    "tenantId", tenant,
                    "roles", List.of("PNC_PROTOCOL_ADAPTER"),
                    "expiresAt", clock.millis() + VALIDITY_MILLIS));
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
            return new IdentityHeaders(payload, signature, tenant);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to sign PnC workload identity", failure);
        }
    }

    private static String requiredTenant(String value) {
        String tenant = value == null ? "" : value.trim();
        if (!tenant.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Configured PnC tenant is invalid");
        }
        return tenant;
    }

    record IdentityHeaders(String payload, String signature, String tenantId) {}
}
