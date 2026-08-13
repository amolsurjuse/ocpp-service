package com.electrahub.ocpp.websocket;

import io.micrometer.core.instrument.MeterRegistry;
import com.electrahub.ocpp.service.ChargerCertificateVerifier;
import com.electrahub.ocpp.service.ChargerCredentialVerifier;
import com.electrahub.ocpp.service.ForwardedClientCertificateParser;
import com.electrahub.ocpp.service.OcppHandshakeRateLimiter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class OcppHandshakeSecurityInterceptor implements HandshakeInterceptor {

    static final String PROTOCOL_ATTRIBUTE = "ocpp.negotiatedProtocol";
    static final String AUTHENTICATION_ATTRIBUTE = "ocpp.authenticationMethod";
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("ocpp1.6", "ocpp2.0.1");

    private final SecurityMode mode;
    private final String sharedSimulatorPassword;
    private final Set<String> allowedOrigins;
    private final MeterRegistry meterRegistry;
    private final ChargerCredentialVerifier credentialVerifier;
    private final OcppHandshakeRateLimiter rateLimiter;
    private final boolean requireSecureTransport;
    private final MtlsMode mtlsMode;
    private final ForwardedClientCertificateParser certificateParser;
    private final ChargerCertificateVerifier certificateVerifier;

    @org.springframework.beans.factory.annotation.Autowired
    public OcppHandshakeSecurityInterceptor(
            @Value("${ocpp.handshake.security-mode:${OCPP_HANDSHAKE_SECURITY_MODE:AUDIT}}") String mode,
            @Value("${ocpp.handshake.simulator-password:${OCPP_HANDSHAKE_SIMULATOR_PASSWORD:}}") String simulatorPassword,
            @Value("${ocpp.handshake.allowed-origins:${OCPP_HANDSHAKE_ALLOWED_ORIGINS:}}") String allowedOrigins,
            MeterRegistry meterRegistry,
            ChargerCredentialVerifier credentialVerifier,
            OcppHandshakeRateLimiter rateLimiter,
            @Value("${ocpp.handshake.require-secure-transport:false}") boolean requireSecureTransport,
            @Value("${ocpp.mtls.mode:${OCPP_MTLS_MODE:DISABLED}}") String mtlsMode,
            ForwardedClientCertificateParser certificateParser,
            ChargerCertificateVerifier certificateVerifier
    ) {
        this.mode = SecurityMode.from(mode);
        this.sharedSimulatorPassword = simulatorPassword == null ? "" : simulatorPassword;
        this.allowedOrigins = parseCsv(allowedOrigins);
        this.meterRegistry = meterRegistry;
        this.credentialVerifier = credentialVerifier;
        this.rateLimiter = rateLimiter;
        this.requireSecureTransport = requireSecureTransport;
        this.mtlsMode = MtlsMode.from(mtlsMode);
        this.certificateParser = certificateParser;
        this.certificateVerifier = certificateVerifier;
    }

    OcppHandshakeSecurityInterceptor(
            String mode,
            String simulatorPassword,
            String allowedOrigins,
            MeterRegistry meterRegistry,
            ChargerCredentialVerifier credentialVerifier,
            OcppHandshakeRateLimiter rateLimiter,
            boolean requireSecureTransport
    ) {
        this(mode, simulatorPassword, allowedOrigins, meterRegistry, credentialVerifier, rateLimiter,
                requireSecureTransport, "DISABLED", new ForwardedClientCertificateParser(),
                (id, certificate, now) -> ChargerCertificateVerifier.Decision.UNREGISTERED);
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (mode == SecurityMode.DISABLED) {
            record("accepted", "none");
            return true;
        }

        String protocol = requestedProtocol(request.getHeaders());
        if (protocol == null) {
            if (!violation(response, HttpStatus.BAD_REQUEST, "missing_protocol")) {
                return false;
            }
        } else if (!SUPPORTED_PROTOCOLS.contains(protocol)) {
            if (!violation(response, HttpStatus.BAD_REQUEST, "unsupported_protocol")) {
                return false;
            }
            protocol = null;
        } else {
            attributes.put(PROTOCOL_ATTRIBUTE, protocol);
        }

        if (!isSecure(request)) {
            if (!violation(
                    response, HttpStatus.UPGRADE_REQUIRED, "insecure_transport", protocolTag(protocol))) {
                return false;
            }
        }

        String origin = request.getHeaders().getOrigin();
        if (origin != null && !origin.isBlank()
                && ((mode == SecurityMode.ENFORCE && !allowedOrigins.contains(origin))
                || (!allowedOrigins.isEmpty() && !allowedOrigins.contains(origin)))) {
            if (!violation(response, HttpStatus.FORBIDDEN, "origin_rejected")) {
                return false;
            }
        }

        String chargePointId = chargePointId(request.getURI());
        OcppHandshakeRateLimiter.Decision limitDecision = rateLimiter.allow(
                chargePointId, request.getRemoteAddress());
        if (limitDecision == OcppHandshakeRateLimiter.Decision.LIMITED) {
            if (!violation(response, HttpStatus.TOO_MANY_REQUESTS, "rate_limited")) {
                return false;
            }
        } else if (limitDecision == OcppHandshakeRateLimiter.Decision.UNAVAILABLE) {
            if (!violation(response, HttpStatus.SERVICE_UNAVAILABLE, "rate_limiter_unavailable")) {
                return false;
            }
        }

        boolean certificateAuthenticated = authenticateCertificate(
                request, response, chargePointId, protocol, attributes);
        if (mtlsMode == MtlsMode.ENFORCE && !certificateAuthenticated) {
            return false;
        }

        Credential credential = parseCredential(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (certificateAuthenticated && mtlsMode == MtlsMode.ENFORCE) {
            record("accepted", protocolTag(protocol));
            return true;
        }
        if (credential == null) {
            if (!violation(response, HttpStatus.UNAUTHORIZED, "missing_credentials")) {
                return false;
            }
        } else {
            if (!MessageDigest.isEqual(
                    chargePointId.getBytes(StandardCharsets.UTF_8),
                    credential.username().getBytes(StandardCharsets.UTF_8))) {
                if (!violation(response, HttpStatus.UNAUTHORIZED, "identity_mismatch")) {
                    return false;
                }
            } else {
                // The transition-only shared fallback must not touch the
                // credential database. It is accepted only in AUDIT; ENFORCE
                // always executes charger-scoped verification below.
                if (mode == SecurityMode.AUDIT && matchesSharedSimulatorPassword(credential.password())) {
                    record("shared_credential", protocolTag(protocol));
                } else {
                    ChargerCredentialVerifier.Decision decision = credentialVerifier.verify(
                            chargePointId, credential.password(), java.time.Instant.now());
                    if (decision != ChargerCredentialVerifier.Decision.VALID
                            && !violation(response, HttpStatus.UNAUTHORIZED, "invalid_credentials")) {
                        return false;
                    }
                }
            }
        }

        record("accepted", protocolTag(protocol));
        attributes.put(AUTHENTICATION_ATTRIBUTE, "basic");
        return true;
    }

    private boolean authenticateCertificate(
            ServerHttpRequest request,
            ServerHttpResponse response,
            String chargePointId,
            String protocol,
            Map<String, Object> attributes
    ) {
        if (mtlsMode == MtlsMode.DISABLED) {
            return false;
        }
        String header = request.getHeaders().getFirst("Client-Cert");
        if (header == null || header.isBlank()) {
            recordMtls("missing_certificate", protocolTag(protocol));
            if (mtlsMode == MtlsMode.ENFORCE) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            return false;
        }
        X509Certificate certificate;
        try {
            certificate = certificateParser.parse(header);
        } catch (IllegalArgumentException exception) {
            recordMtls("malformed_certificate", protocolTag(protocol));
            if (mtlsMode == MtlsMode.ENFORCE) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
            }
            return false;
        }
        ChargerCertificateVerifier.Decision decision = certificateVerifier.verify(
                chargePointId, certificate, Instant.now());
        String outcome = switch (decision) {
            case VALID -> "accepted";
            case MALFORMED -> "malformed_certificate";
            case IDENTITY_MISMATCH -> "identity_mismatch";
            case UNREGISTERED -> "unregistered_certificate";
            case INACTIVE -> "inactive_certificate";
            case NOT_YET_VALID -> "not_yet_valid_certificate";
            case EXPIRED -> "expired_certificate";
            case WEAK_KEY -> "weak_key";
        };
        recordMtls(outcome, protocolTag(protocol));
        if (decision == ChargerCertificateVerifier.Decision.VALID) {
            attributes.put(AUTHENTICATION_ATTRIBUTE, "mtls");
            return true;
        }
        if (mtlsMode == MtlsMode.ENFORCE) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
        }
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // No credential or authorization state is retained after the upgrade.
    }

    private boolean violation(ServerHttpResponse response, HttpStatus status, String outcome) {
        return violation(response, status, outcome, "none");
    }

    private boolean violation(ServerHttpResponse response, HttpStatus status, String outcome, String protocol) {
        record(outcome, protocol);
        if (mode != SecurityMode.ENFORCE
                || ("insecure_transport".equals(outcome) && !requireSecureTransport)) {
            return true;
        }
        response.setStatusCode(status);
        return false;
    }

    private void record(String outcome, String protocol) {
        meterRegistry.counter("electrahub.ocpp.handshake", "outcome", outcome, "protocol", protocol).increment();
    }

    private void recordMtls(String outcome, String protocol) {
        meterRegistry.counter("electrahub.ocpp.mtls.handshake",
                "outcome", outcome, "protocol", protocol).increment();
    }

    private boolean matchesSharedSimulatorPassword(String suppliedPassword) {
        return !sharedSimulatorPassword.isBlank()
                && suppliedPassword != null
                && MessageDigest.isEqual(
                        sharedSimulatorPassword.getBytes(StandardCharsets.UTF_8),
                        suppliedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private static String requestedProtocol(HttpHeaders headers) {
        for (String value : headers.getOrEmpty("Sec-WebSocket-Protocol")) {
            for (String candidate : value.split(",")) {
                String normalized = candidate.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private static boolean isSecure(ServerHttpRequest request) {
        if ("wss".equalsIgnoreCase(request.getURI().getScheme())
                || "https".equalsIgnoreCase(request.getURI().getScheme())) {
            return true;
        }
        return request.getHeaders().getOrEmpty("X-Forwarded-Proto").stream()
                .anyMatch(value -> "https".equalsIgnoreCase(value.trim()));
    }

    private static Credential parseCredential(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            return new Credential(decoded.substring(0, separator), decoded.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String chargePointId(URI uri) {
        String path = uri == null ? "" : uri.getPath();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1 ? path.substring(lastSlash + 1) : "";
    }

    private static Set<String> parseCsv(String value) {
        Set<String> values = new HashSet<>();
        if (value != null) {
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    values.add(item.trim());
                }
            }
        }
        return Set.copyOf(values);
    }

    private static String protocolTag(String protocol) {
        return protocol == null ? "none" : protocol;
    }

    private enum SecurityMode {
        DISABLED,
        AUDIT,
        ENFORCE;

        private static SecurityMode from(String value) {
            try {
                return valueOf(value == null ? "AUDIT" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return AUDIT;
            }
        }
    }

    private enum MtlsMode {
        DISABLED,
        AUDIT,
        ENFORCE;

        private static MtlsMode from(String value) {
            try {
                return valueOf(value == null ? "DISABLED" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return DISABLED;
            }
        }
    }

    private record Credential(String username, String password) {
    }
}
