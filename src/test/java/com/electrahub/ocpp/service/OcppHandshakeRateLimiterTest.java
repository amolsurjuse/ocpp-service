package com.electrahub.ocpp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class OcppHandshakeRateLimiterTest {

    @Test
    void allowsAttemptsInsideTheDistributedWindow() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any())).thenReturn(10L);

        OcppHandshakeRateLimiter limiter = new OcppHandshakeRateLimiter(redis, true, 30);

        assertThat(limiter.allow("EH-IN-001", new InetSocketAddress("192.0.2.1", 1234)))
                .isEqualTo(OcppHandshakeRateLimiter.Decision.ALLOWED);
    }

    @Test
    void limitsAttemptsOutsideTheWindowAndFailsClosedWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any()))
                .thenReturn(31L)
                .thenThrow(new IllegalStateException("redis unavailable"));
        OcppHandshakeRateLimiter limiter = new OcppHandshakeRateLimiter(redis, true, 30);

        assertThat(limiter.allow("EH-IN-001", null))
                .isEqualTo(OcppHandshakeRateLimiter.Decision.LIMITED);
        assertThat(limiter.allow("EH-IN-001", null))
                .isEqualTo(OcppHandshakeRateLimiter.Decision.UNAVAILABLE);
    }
}
