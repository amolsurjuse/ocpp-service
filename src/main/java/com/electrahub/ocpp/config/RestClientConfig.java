package com.electrahub.ocpp.config;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientConfig.class);


    /**
     * Executes rest client builder for `RestClientConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.config`.
     * @return result produced by restClientBuilder.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        LOGGER.info(" Entering RestClientConfig#restClientBuilder");
        LOGGER.debug(" Entering RestClientConfig#restClientBuilder with debug context");
        return RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    addTraceHeaders(request.getHeaders());
                    return execution.execute(request, body);
                });
    }

    private void addTraceHeaders(org.springframework.http.HttpHeaders headers) {
        String traceId = MDC.get("traceId");
        String spanId = MDC.get("spanId");
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = "0000000000000000";
        }
        headers.set("X-Trace-Id", traceId);
        headers.set("X-Span-Id", spanId);
        headers.set("X-B3-TraceId", traceId);
        headers.set("X-B3-SpanId", spanId);
        if (isHex(traceId, 32) && isHex(spanId, 16)) {
            headers.set("traceparent", "00-" + traceId + "-" + spanId + "-01");
        }
    }

    private boolean isHex(String value, int length) {
        return value != null && value.length() == length && value.matches("[0-9a-fA-F]+");
    }
}
