package com.electrahub.ocpp.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "ocpp.iso15118.certificate-installation-enabled", havingValue = "true")
public class PncCertificateInstallationClient {
    private static final Logger log = LoggerFactory.getLogger(PncCertificateInstallationClient.class);
    private static final int MAX_EXI_BYTES = 48_000;
    private static final int MAX_EXI_BASE64_CHARS = 64_000;

    private final RestClient client;
    private final PncWorkloadIdentitySigner identity;
    private final MeterRegistry metrics;

    public PncCertificateInstallationClient(
            RestClient.Builder builder,
            PncWorkloadIdentitySigner identity,
            MeterRegistry metrics,
            @Value("${integration.pnc.base-url:${PNC_SERVICE_URL:http://plug-and-charge-platform:8098}}")
            String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.identity = identity;
        this.metrics = metrics;
    }

    public Result install(String tenantId, Request request) {
        long started = System.nanoTime();
        String outcome = "failed";
        try {
            PncWorkloadIdentitySigner.IdentityHeaders headers = identity.sign(tenantId);
            Response response = client.post()
                    .uri("/api/v1/internal/iso15118/certificate-installations")
                    .header(PncWorkloadIdentitySigner.CONTEXT_HEADER, headers.payload())
                    .header(PncWorkloadIdentitySigner.SIGNATURE_HEADER, headers.signature())
                    .header(PncWorkloadIdentitySigner.TENANT_HEADER, headers.tenantId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Response.class);
            if (response == null || !"Accepted".equals(response.status())
                    || !validExi(response.exiResponse())) {
                return Result.failed();
            }
            outcome = "accepted";
            return Result.accepted(response.exiResponse());
        } catch (RuntimeException failure) {
            log.warn("PnC certificate installation failed for station={} messageId={} category={}",
                    request.chargingStationId(), request.ocppMessageId(), failure.getClass().getSimpleName());
            return Result.failed();
        } finally {
            metrics.counter("electrahub.ocpp.iso15118.certificate_installation", "outcome", outcome).increment();
            Timer.builder("electrahub.ocpp.iso15118.certificate_installation.duration")
                    .register(metrics).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    static boolean validExi(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_EXI_BASE64_CHARS) return false;
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return decoded.length > 0 && decoded.length <= MAX_EXI_BYTES;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public record Request(String chargingStationId, String ocppMessageId, String action,
                          String schemaVersion, String exiRequestBase64) {}

    private record Response(String status, String exiResponse, String failureCategory, String exchangeId) {}

    public record Result(boolean accepted, String exiResponse) {
        static Result accepted(String exi) { return new Result(true, exi); }
        static Result failed() { return new Result(false, null); }
    }
}
