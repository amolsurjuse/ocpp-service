package com.electrahub.ocpp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
public class SignedPncCertificateInstallationClient implements PncCertificateInstallationClient {
    private static final String PATH = "/api/v1/internal/iso15118/certificate-installations";
    private static final UUID ACTOR_ID = UUID.nameUUIDFromBytes(
            "ocpp-pnc-protocol-adapter".getBytes(StandardCharsets.UTF_8));
    private final RestClient client;
    private final ObjectMapper json;
    private final String tenantId;
    private final byte[] identitySecret;

    public SignedPncCertificateInstallationClient(
            RestClient.Builder builder,
            ObjectMapper json,
            @Value("${integration.pnc-service.base-url:http://plug-and-charge-platform:8098}") String baseUrl,
            @Value("${ocpp.iso15118.tenant-id:electrahub}") String tenantId,
            @Value("${ocpp.iso15118.identity-secret:}") String identitySecret) {
        if (identitySecret == null || identitySecret.isBlank() || identitySecret.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("OCPP_ISO15118_IDENTITY_SECRET is required when certificate installation is enabled");
        }
        this.client = builder.baseUrl(baseUrl).build();
        this.json = json;
        this.tenantId = required(tenantId, "tenant-id");
        this.identitySecret = identitySecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Response install(Request request) {
        String context = identityContext();
        return client.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-ElectraHub-Tenant-Id", tenantId)
                .header("X-ElectraHub-Identity-Context", context)
                .header("X-ElectraHub-Identity-Context-Signature", sign(context))
                .body(request)
                .retrieve()
                .body(Response.class);
    }

    private String identityContext() {
        try {
            byte[] payload = json.writeValueAsBytes(new IdentityPayload(
                    1, ACTOR_ID.toString(), tenantId, List.of("PNC_PROTOCOL_ADAPTER"),
                    System.currentTimeMillis() + 30_000));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create PnC workload identity", exception);
        }
    }

    private String sign(String context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(identitySecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(context.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign PnC workload identity", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException(field + " is required");
        return value.trim();
    }

    private record IdentityPayload(int version, String userId, String tenantId,
                                   List<String> roles, long expiresAt) { }
}
