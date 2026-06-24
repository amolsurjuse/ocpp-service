package com.electrahub.ocpp.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceMdcFilter extends OncePerRequestFilter {
    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String TRACEPARENT = "traceparent";
    private static final String X_TRACE_ID = "X-Trace-Id";
    private static final String X_SPAN_ID = "X-Span-Id";
    private static final String X_B3_TRACE_ID = "X-B3-TraceId";
    private static final String X_B3_SPAN_ID = "X-B3-SpanId";

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceMdcFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer != null ? tracer.currentSpan() : null;
        String traceId = firstNonBlank(spanTraceId(span), traceIdFromTraceparent(request.getHeader(TRACEPARENT)),
                request.getHeader(X_TRACE_ID), request.getHeader(X_B3_TRACE_ID), generateTraceId());
        String spanId = firstNonBlank(spanId(span), spanIdFromTraceparent(request.getHeader(TRACEPARENT)),
                request.getHeader(X_SPAN_ID), request.getHeader(X_B3_SPAN_ID), generateSpanId());
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put(TRACE_ID, traceId);
            MDC.put(SPAN_ID, spanId);
            response.setHeader(X_TRACE_ID, traceId);
            response.setHeader(X_SPAN_ID, spanId);
            if (isHex(traceId, 32) && isHex(spanId, 16)) {
                response.setHeader(TRACEPARENT, "00-" + traceId + "-" + spanId + "-01");
            }
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null || previous.isEmpty()) MDC.clear(); else MDC.setContextMap(previous);
        }
    }

    private String spanTraceId(Span span) { return span == null || span.isNoop() ? "" : span.context().traceId(); }
    private String spanId(Span span) { return span == null || span.isNoop() ? "" : span.context().spanId(); }
    private String traceIdFromTraceparent(String traceparent) {
        String[] parts = traceparent == null ? new String[0] : traceparent.split("-");
        return parts.length >= 4 && isHex(parts[1], 32) ? parts[1] : "";
    }
    private String spanIdFromTraceparent(String traceparent) {
        String[] parts = traceparent == null ? new String[0] : traceparent.split("-");
        return parts.length >= 4 && isHex(parts[2], 16) ? parts[2] : "";
    }
    private boolean isHex(String value, int length) { return value != null && value.length() == length && value.matches("[0-9a-fA-F]+"); }
    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }
    private String generateTraceId() { return UUID.randomUUID().toString().replace("-", ""); }
    private String generateSpanId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
}
