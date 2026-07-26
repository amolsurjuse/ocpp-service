package com.electrahub.ocpp.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcppAuthorizationGrantServiceTest {
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private OcppAuthorizationGrantService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        service = new OcppAuthorizationGrantService(redisTemplate, new SimpleMeterRegistry(), 300);
    }

    @Test
    void acceptsTheSameTransactionAfterATransientReconnectReplay() {
        when(values.get(anyString())).thenReturn(null, null, "grant-1");

        assertThat(service.consumeForStart("EH-US-CHG-0001", 1, "RFID-APPROVED", 810001)).isTrue();

        String bindingKey = bindingKey("RFID-APPROVED");
        String binding = "grant-1:1:810001";
        when(values.get(anyString())).thenAnswer(invocation ->
                bindingKey.equals(invocation.getArgument(0)) ? binding : "grant-1"
        );

        assertThat(service.consumeForStart("EH-US-CHG-0001", 1, "RFID-APPROVED", 810001)).isTrue();
        verify(values).set(bindingKey, binding, Duration.ofSeconds(300));
    }

    @Test
    void rejectsASecondTransactionUnderTheSameAuthorizationGrant() {
        String bindingKey = bindingKey("RFID-APPROVED");
        when(values.get(anyString())).thenAnswer(invocation ->
                bindingKey.equals(invocation.getArgument(0)) ? "grant-1:1:810001" : "grant-1"
        );

        assertThat(service.consumeForStart("EH-US-CHG-0001", 1, "RFID-APPROVED", 810002)).isFalse();
    }

    @Test
    void rejectsAnUnknownRfidBeforeItCanStartATransaction() {
        when(values.get(anyString())).thenReturn(null);

        assertThat(service.consumeForStart("EH-US-CHG-0001", 1, "RFID-UNKNOWN", 810003)).isFalse();
    }

    @Test
    void carriesRemoteStartCorrelationIntoTheBoundTransaction() {
        String correlationId = "4dd9ed1d-3d0c-4470-aa12-a78a9be56339";
        String binding = "grant-1|" + correlationId + ":1:810004";
        when(values.get(bindingKey("RFID-APPROVED"))).thenReturn(binding);

        assertThat(service.correlationForStart("EH-US-CHG-0001", "RFID-APPROVED"))
                .isEqualTo(correlationId);
    }

    private String bindingKey(String idTag) {
        return "ocpp:authorization-grant:start:EH-US-CHG-0001:" + tokenHash(idTag);
    }

    private String tokenHash(String idTag) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(idTag.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
