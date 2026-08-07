package com.electrahub.ocpp.service;

import com.electrahub.ocpp.exception.SmartChargingIdempotencyConflictException;
import com.electrahub.ocpp.exception.SmartChargingIdempotencyUnavailableException;
import com.electrahub.ocpp.web.dto.SmartChargingCommandResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class SmartChargingIdempotencyService {

    private static final String PREFIX = "ocpp:smart-charging:idempotency:";

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;
    private final Duration resultTtl;
    private final Duration claimTtl;
    private final ConcurrentHashMap<String, InFlight> inFlight = new ConcurrentHashMap<>();

    public SmartChargingIdempotencyService(
            RedisTemplate<String, String> redis,
            ObjectMapper objectMapper,
            @Value("${ocpp.smart-charging.idempotency-result-ttl-seconds:172800}") long resultTtlSeconds,
            @Value("${ocpp.smart-charging.idempotency-claim-ttl-seconds:60}") long claimTtlSeconds
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.resultTtl = Duration.ofSeconds(Math.max(3600, resultTtlSeconds));
        this.claimTtl = Duration.ofSeconds(Math.max(30, claimTtlSeconds));
    }

    public CompletableFuture<SmartChargingCommandResponse> execute(
            String scope,
            String requestHash,
            Supplier<CompletableFuture<SmartChargingCommandResponse>> operation
    ) {
        String keyHash = sha256(scope);
        String resultKey = PREFIX + "result:" + keyHash;
        String claimKey = PREFIX + "claim:" + keyHash;
        StoredResult completed = readResult(resultKey);
        if (completed != null) {
            requireSameHash(completed.requestHash(), requestHash);
            return CompletableFuture.completedFuture(completed.response().asReplay());
        }

        InFlight local = inFlight.get(keyHash);
        if (local != null) {
            requireSameHash(local.requestHash(), requestHash);
            return local.future().thenApply(SmartChargingCommandResponse::asReplay);
        }

        CompletableFuture<SmartChargingCommandResponse> shared = new CompletableFuture<>();
        InFlight created = new InFlight(requestHash, shared);
        InFlight raced = inFlight.putIfAbsent(keyHash, created);
        if (raced != null) {
            requireSameHash(raced.requestHash(), requestHash);
            return raced.future().thenApply(SmartChargingCommandResponse::asReplay);
        }

        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(claimKey, requestHash, claimTtl);
            if (!Boolean.TRUE.equals(claimed)) {
                String claimHash = redis.opsForValue().get(claimKey);
                requireSameHash(claimHash, requestHash);
                waitForDistributedResult(resultKey, claimKey, requestHash, shared);
                return shared;
            }
            operation.get().whenComplete((response, throwable) -> {
                try {
                    if (throwable == null) {
                        redis.opsForValue().set(resultKey, writeResult(requestHash, response), resultTtl);
                        shared.complete(response);
                    } else {
                        shared.completeExceptionally(throwable);
                    }
                } catch (RuntimeException ex) {
                    shared.completeExceptionally(new SmartChargingIdempotencyUnavailableException(
                            "Smart-charging idempotency result could not be persisted", ex));
                } finally {
                    safeDelete(claimKey);
                    inFlight.remove(keyHash, created);
                }
            });
            return shared;
        } catch (SmartChargingIdempotencyConflictException ex) {
            inFlight.remove(keyHash, created);
            throw ex;
        } catch (RuntimeException ex) {
            inFlight.remove(keyHash, created);
            throw new SmartChargingIdempotencyUnavailableException(
                    "Smart-charging idempotency store is unavailable", ex);
        }
    }

    private void waitForDistributedResult(
            String resultKey,
            String claimKey,
            String requestHash,
            CompletableFuture<SmartChargingCommandResponse> future
    ) {
        CompletableFuture.runAsync(() -> {
            long deadline = System.nanoTime() + claimTtl.toNanos();
            try {
                while (System.nanoTime() < deadline) {
                    StoredResult result = readResult(resultKey);
                    if (result != null) {
                        requireSameHash(result.requestHash(), requestHash);
                        future.complete(result.response().asReplay());
                        return;
                    }
                    if (!Boolean.TRUE.equals(redis.hasKey(claimKey))) {
                        future.completeExceptionally(new SmartChargingIdempotencyUnavailableException(
                                "The original smart-charging request ended without a replayable result", null));
                        return;
                    }
                    TimeUnit.MILLISECONDS.sleep(100);
                }
                future.completeExceptionally(new SmartChargingIdempotencyUnavailableException(
                        "Timed out waiting for the original smart-charging request", null));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(ex);
            } catch (RuntimeException ex) {
                future.completeExceptionally(new SmartChargingIdempotencyUnavailableException(
                        "Smart-charging idempotency store is unavailable", ex));
            } finally {
                inFlight.entrySet().removeIf(entry -> entry.getValue().future() == future);
            }
        });
    }

    private StoredResult readResult(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, StoredResult.class);
        } catch (JsonProcessingException ex) {
            throw new SmartChargingIdempotencyUnavailableException("Stored idempotency result is invalid", ex);
        } catch (SmartChargingIdempotencyUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new SmartChargingIdempotencyUnavailableException(
                    "Smart-charging idempotency store is unavailable", ex);
        }
    }

    private String writeResult(String requestHash, SmartChargingCommandResponse response) {
        try {
            return objectMapper.writeValueAsString(new StoredResult(requestHash, response));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize smart-charging result", ex);
        }
    }

    private void requireSameHash(String existing, String requested) {
        if (existing == null || !MessageDigest.isEqual(
                existing.getBytes(StandardCharsets.US_ASCII), requested.getBytes(StandardCharsets.US_ASCII))) {
            throw new SmartChargingIdempotencyConflictException(
                    "The idempotency key is already associated with a different smart-charging request");
        }
    }

    private void safeDelete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // Claim TTL is the recovery path; do not replace a completed command result.
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record InFlight(String requestHash, CompletableFuture<SmartChargingCommandResponse> future) {
    }

    private record StoredResult(String requestHash, SmartChargingCommandResponse response) {
    }
}
