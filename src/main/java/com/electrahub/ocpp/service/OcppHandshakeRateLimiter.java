package com.electrahub.ocpp.service;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class OcppHandshakeRateLimiter {

    public enum Decision {
        ALLOWED,
        LIMITED,
        UNAVAILABLE
    }

    private static final DefaultRedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final long attemptsPerMinute;

    public OcppHandshakeRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${ocpp.handshake.rate-limit-enabled:true}") boolean enabled,
            @Value("${ocpp.handshake.rate-limit-attempts-per-minute:30}") long attemptsPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.attemptsPerMinute = Math.max(1, attemptsPerMinute);
    }

    public Decision allow(String chargePointId, InetSocketAddress remoteAddress) {
        if (!enabled) {
            return Decision.ALLOWED;
        }
        String remote = remoteAddress == null || remoteAddress.getAddress() == null
                ? "unknown"
                : remoteAddress.getAddress().getHostAddress();
        String key = "ocpp:handshake-rate:" + sha256(chargePointId + "|" + remote);
        try {
            Long attempts = redisTemplate.execute(WINDOW_SCRIPT, List.of(key), "60");
            return attempts != null && attempts <= attemptsPerMinute ? Decision.ALLOWED : Decision.LIMITED;
        } catch (RuntimeException exception) {
            return Decision.UNAVAILABLE;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
