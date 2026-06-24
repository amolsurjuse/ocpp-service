package com.electrahub.ocpp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-ElectraHub-Internal-Token";

    private final String internalToken;

    public InternalServiceTokenFilter(
            @Value("${app.security.internal-token:}") String internalToken
    ) {
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresInternalToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (internalToken.isBlank() || !matches(request.getHeader(HEADER_NAME))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"INTERNAL_AUTH_REQUIRED\",\"message\":\"Internal service token is required\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresInternalToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/v1/ocpp/commands/");
    }

    private boolean matches(String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                providedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
