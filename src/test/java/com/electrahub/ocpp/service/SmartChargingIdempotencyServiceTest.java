package com.electrahub.ocpp.service;

import com.electrahub.ocpp.exception.SmartChargingIdempotencyConflictException;
import com.electrahub.ocpp.exception.SmartChargingIdempotencyUnavailableException;
import com.electrahub.ocpp.web.dto.SmartChargingCommandResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartChargingIdempotencyServiceTest {

    @Test
    void joinsSameNodeInFlightRequestAndMarksJoinedResponseAsReplay() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        SmartChargingIdempotencyService service = new SmartChargingIdempotencyService(
                redis, new ObjectMapper(), 3600, 60);
        CompletableFuture<SmartChargingCommandResponse> operation = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<SmartChargingCommandResponse> first = service.execute(
                "CP-1:set:key", "hash-a", () -> { calls.incrementAndGet(); return operation; });
        CompletableFuture<SmartChargingCommandResponse> second = service.execute(
                "CP-1:set:key", "hash-a", () -> { calls.incrementAndGet(); return operation; });
        operation.complete(response(false));

        assertThat(first.join().replayed()).isFalse();
        assertThat(second.join().replayed()).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsDifferentRequestForSameInFlightKey() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        SmartChargingIdempotencyService service = new SmartChargingIdempotencyService(
                redis, new ObjectMapper(), 3600, 60);
        CompletableFuture<SmartChargingCommandResponse> operation = new CompletableFuture<>();
        service.execute("CP-1:set:key", "hash-a", () -> operation);

        assertThatThrownBy(() -> service.execute("CP-1:set:key", "hash-b", () -> operation))
                .isInstanceOf(SmartChargingIdempotencyConflictException.class);
        operation.complete(response(false));
    }

    @Test
    void replaysCompletedRedisResultWithoutExecutingCommandAgain() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String stored = """
                {"requestHash":"hash-a","response":{"protocol":"OCPP16J","action":"SetChargingProfile",\
                "profileId":1,"chargerStatus":"Accepted","accepted":true,"replayed":false,\
                "payload":{"status":"Accepted"}}}
                """;
        when(values.get(anyString())).thenReturn(stored);
        SmartChargingIdempotencyService service = new SmartChargingIdempotencyService(
                redis, new ObjectMapper(), 3600, 60);
        AtomicInteger calls = new AtomicInteger();

        SmartChargingCommandResponse result = service.execute(
                "CP-1:set:key", "hash-a", () -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(response(false));
                }).join();

        assertThat(result.replayed()).isTrue();
        assertThat(result.accepted()).isTrue();
        assertThat(calls).hasValue(0);
    }

    @Test
    void failsClosedWhenRedisCannotReadIdempotencyState() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));
        SmartChargingIdempotencyService service = new SmartChargingIdempotencyService(
                redis, new ObjectMapper(), 3600, 60);

        assertThatThrownBy(() -> service.execute(
                "CP-1:set:key", "hash-a", () -> CompletableFuture.completedFuture(response(false))))
                .isInstanceOf(SmartChargingIdempotencyUnavailableException.class);
    }

    private SmartChargingCommandResponse response(boolean replayed) {
        return new SmartChargingCommandResponse(
                "OCPP16J", "SetChargingProfile", 1, "Accepted", true, replayed, Map.of("status", "Accepted"));
    }
}
