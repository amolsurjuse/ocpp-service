package com.electrahub.ocpp.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.electrahub.ocpp.service.ChargerCredentialVerifier;
import com.electrahub.ocpp.service.OcppHandshakeRateLimiter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class OcppHandshakeSecurityInterceptorTest {

    @Test
    void auditModeAcceptsLegacyClientAndRecordsMissingControls() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OcppHandshakeSecurityInterceptor interceptor = interceptor("AUDIT", registry);

        boolean accepted = interceptor.beforeHandshake(
                request(new HttpHeaders()), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isTrue();
        assertThat(registry.get("electrahub.ocpp.handshake")
                .tag("outcome", "missing_protocol").counter().count()).isEqualTo(1);
        assertThat(registry.get("electrahub.ocpp.handshake")
                .tag("outcome", "missing_credentials").counter().count()).isEqualTo(1);
    }

    @Test
    void enforceModeAcceptsMatchingIdentityCredentialAndProtocol() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp1.6");
        headers.setBasicAuth("EH-TEST-001", "simulator-secret", StandardCharsets.UTF_8);
        HashMap<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor("ENFORCE", registry).beforeHandshake(
                request(headers), mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(OcppHandshakeSecurityInterceptor.PROTOCOL_ATTRIBUTE, "ocpp1.6");
    }

    @Test
    void enforceModeRejectsUnsupportedVersion() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp2.1");
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor("ENFORCE", registry).beforeHandshake(
                request(headers), response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enforceModeRejectsPathUsernameMismatchWithoutExposingPassword() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp2.0.1");
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(
                "OTHER:simulator-secret".getBytes(StandardCharsets.UTF_8)));
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        boolean accepted = interceptor("ENFORCE", new SimpleMeterRegistry()).beforeHandshake(
                request(headers), response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void enforceModeNeverAcceptsTheSharedSimulatorFallback() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp1.6");
        headers.setBasicAuth("EH-TEST-001", "simulator-secret", StandardCharsets.UTF_8);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        OcppHandshakeSecurityInterceptor interceptor = new OcppHandshakeSecurityInterceptor(
                "ENFORCE", "simulator-secret", "", registry,
                (id, password, now) -> ChargerCredentialVerifier.Decision.NOT_FOUND,
                rateLimiter(OcppHandshakeRateLimiter.Decision.ALLOWED), false);

        boolean accepted = interceptor.beforeHandshake(
                request(headers), response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void auditModeReportsTransitionOnlySharedCredentialUse() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp1.6");
        headers.setBasicAuth("EH-TEST-001", "simulator-secret", StandardCharsets.UTF_8);
        ChargerCredentialVerifier verifier = mock(ChargerCredentialVerifier.class);
        OcppHandshakeSecurityInterceptor interceptor = new OcppHandshakeSecurityInterceptor(
                "AUDIT", "simulator-secret", "", registry,
                verifier,
                rateLimiter(OcppHandshakeRateLimiter.Decision.ALLOWED), false);

        boolean accepted = interceptor.beforeHandshake(
                request(headers), mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isTrue();
        assertThat(registry.get("electrahub.ocpp.handshake")
                .tag("outcome", "shared_credential").counter().count()).isEqualTo(1);
        verifyNoInteractions(verifier);
    }

    @Test
    void enforceModeRejectsAStationThatExceedsTheDistributedHandshakeLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp1.6");
        headers.setBasicAuth("EH-TEST-001", "individual-secret", StandardCharsets.UTF_8);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        OcppHandshakeSecurityInterceptor interceptor = new OcppHandshakeSecurityInterceptor(
                "ENFORCE", "", "", new SimpleMeterRegistry(),
                (id, password, now) -> ChargerCredentialVerifier.Decision.VALID,
                rateLimiter(OcppHandshakeRateLimiter.Decision.LIMITED), false);

        boolean accepted = interceptor.beforeHandshake(
                request(headers), response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void enforceModeCanRequireTheProfileTwoTlsBoundary() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", "ocpp1.6");
        headers.setBasicAuth("EH-TEST-001", "individual-secret", StandardCharsets.UTF_8);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        OcppHandshakeSecurityInterceptor interceptor = new OcppHandshakeSecurityInterceptor(
                "ENFORCE", "", "", new SimpleMeterRegistry(),
                (id, password, now) -> ChargerCredentialVerifier.Decision.VALID,
                rateLimiter(OcppHandshakeRateLimiter.Decision.ALLOWED), true);

        boolean accepted = interceptor.beforeHandshake(
                request(headers), response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UPGRADE_REQUIRED);
    }

    private OcppHandshakeSecurityInterceptor interceptor(String mode, SimpleMeterRegistry registry) {
        return new OcppHandshakeSecurityInterceptor(
                mode, "simulator-secret", "", registry,
                (id, password, now) -> ChargerCredentialVerifier.Decision.VALID,
                rateLimiter(OcppHandshakeRateLimiter.Decision.ALLOWED), false);
    }

    private OcppHandshakeRateLimiter rateLimiter(OcppHandshakeRateLimiter.Decision decision) {
        OcppHandshakeRateLimiter limiter = mock(OcppHandshakeRateLimiter.class);
        when(limiter.allow(any(), any())).thenReturn(decision);
        return limiter;
    }

    private ServerHttpRequest request(HttpHeaders headers) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws/ocpp/EH-TEST-001"));
        return request;
    }
}
