package com.electrahub.ocpp.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenFilterTest {
    @Test
    void sessionEvidenceRejectsMissingInternalCredential() throws Exception {
        var filter = new InternalServiceTokenFilter("internal-test-token");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/ocpp/internal/session-evidence"), response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    private static final String STATUS_PATH = "/api/v1/ocpp/commands/remote-start/status";

    @Test
    void remoteStartStatusQueryRejectsMissingInternalToken() throws Exception {
        InternalServiceTokenFilter filter = new InternalServiceTokenFilter("internal-test-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", STATUS_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("INTERNAL_AUTH_REQUIRED");
    }

    @Test
    void remoteStartStatusQueryAcceptsConfiguredInternalToken() throws Exception {
        InternalServiceTokenFilter filter = new InternalServiceTokenFilter("internal-test-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", STATUS_PATH);
        request.addHeader(InternalServiceTokenFilter.HEADER_NAME, "internal-test-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
