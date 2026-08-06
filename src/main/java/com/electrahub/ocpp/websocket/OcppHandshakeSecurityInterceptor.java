package com.electrahub.ocpp.websocket;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("ocpp1.6", "ocpp2.0.1");

    private final SecurityMode mode;
    private final String simulatorPassword;
    private final Set<String> allowedOrigins;
    private final MeterRegistry meterRegistry;

    public OcppHandshakeSecurityInterceptor(
            @Value("${ocpp.handshake.security-mode:${OCPP_HANDSHAKE_SECURITY_MODE:AUDIT}}") String mode,
            @Value("${ocpp.handshake.simulator-password:${OCPP_HANDSHAKE_SIMULATOR_PASSWORD:}}") String simulatorPassword,
            @Value("${ocpp.handshake.allowed-origins:${OCPP_HANDSHAKE_ALLOWED_ORIGINS:}}") String allowedOrigins,
            MeterRegistry meterRegistry
    ) {
        this.mode = SecurityMode.from(mode);
        this.simulatorPassword = simulatorPassword == null ? "" : simulatorPassword;
        this.allowedOrigins = parseCsv(allowedOrigins);
        this.meterRegistry = meterRegistry;
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
            record("insecure_transport", protocolTag(protocol));
        }

        String origin = request.getHeaders().getOrigin();
        if (origin != null && !origin.isBlank() && !allowedOrigins.isEmpty() && !allowedOrigins.contains(origin)) {
            if (!violation(response, HttpStatus.FORBIDDEN, "origin_rejected")) {
                return false;
            }
        }

        Credential credential = parseCredential(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (credential == null) {
            if (!violation(response, HttpStatus.UNAUTHORIZED, "missing_credentials")) {
                return false;
            }
        } else {
            String chargePointId = chargePointId(request.getURI());
            if (!MessageDigest.isEqual(
                    chargePointId.getBytes(StandardCharsets.UTF_8),
                    credential.username().getBytes(StandardCharsets.UTF_8))) {
                if (!violation(response, HttpStatus.UNAUTHORIZED, "identity_mismatch")) {
                    return false;
                }
            } else if (simulatorPassword.isBlank() || !MessageDigest.isEqual(
                    simulatorPassword.getBytes(StandardCharsets.UTF_8),
                    credential.password().getBytes(StandardCharsets.UTF_8))) {
                if (!violation(response, HttpStatus.UNAUTHORIZED, "invalid_credentials")) {
                    return false;
                }
            }
        }

        record("accepted", protocolTag(protocol));
        return true;
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
        record(outcome, "none");
        if (mode != SecurityMode.ENFORCE) {
            return true;
        }
        response.setStatusCode(status);
        return false;
    }

    private void record(String outcome, String protocol) {
        meterRegistry.counter("electrahub.ocpp.handshake", "outcome", outcome, "protocol", protocol).increment();
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

    private record Credential(String username, String password) {
    }
}
