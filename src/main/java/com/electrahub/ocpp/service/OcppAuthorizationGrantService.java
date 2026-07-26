package com.electrahub.ocpp.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;

/**
 * Stores a short-lived proof that an OCPP token was accepted before a charger
 * sends its StartTransaction. The grant is connector-scoped for remote starts
 * and deliberately does not store the raw RFID/eMAID value. The first accepted
 * start is bound to its OCPP transaction ID, allowing an identical start event
 * to be replayed safely after a CSMS or connector reconnect.
 */
@Component
@Slf4j
public class OcppAuthorizationGrantService {
    private static final String PREFIX = "ocpp:authorization-grant:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration grantTtl;
    private final Counter granted;
    private final Counter consumed;
    private final Counter replayed;
    private final Counter rejected;
    private final Counter missing;
    private final Counter redisFailures;

    public OcppAuthorizationGrantService(
            RedisTemplate<String, String> redisTemplate,
            MeterRegistry meterRegistry,
            @Value("${ocpp.authorization-grant.ttl-seconds:300}") long grantTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.grantTtl = Duration.ofSeconds(Math.max(30L, grantTtlSeconds));
        this.granted = meterRegistry.counter("ocpp.authorization.grants", "outcome", "granted");
        this.consumed = meterRegistry.counter("ocpp.authorization.grants", "outcome", "consumed");
        this.replayed = meterRegistry.counter("ocpp.authorization.grants", "outcome", "replayed");
        this.rejected = meterRegistry.counter("ocpp.authorization.grants", "outcome", "rejected");
        this.missing = meterRegistry.counter("ocpp.authorization.grants", "outcome", "missing");
        this.redisFailures = meterRegistry.counter("ocpp.authorization.grants", "outcome", "redis_failure");
    }

    public boolean grantAuthorization(String chargePointId, String idTag) {
        return store(authorizationKey(chargePointId, idTag));
    }

    public boolean grantRemoteStart(String chargePointId, Integer connectorId, String idTag) {
        return grantRemoteStart(chargePointId, connectorId, idTag, null);
    }

    public boolean grantRemoteStart(String chargePointId, Integer connectorId, String idTag, String correlationId) {
        return store(remoteStartKey(chargePointId, connectorId, idTag), correlationId);
    }

    public void revokeRemoteStart(String chargePointId, Integer connectorId, String idTag) {
        try {
            redisTemplate.delete(remoteStartKey(chargePointId, connectorId, idTag));
        } catch (DataAccessException exception) {
            redisFailures.increment();
            log.warn("Unable to revoke OCPP remote-start authorization grant for chargePointId={} connectorId={}",
                    safeChargePointId(chargePointId), normalizedConnector(connectorId), exception);
        }
    }

    /**
     * Validates a remote-start grant first, then a charger-side authorization
     * grant. The selected grant is bound to the first transaction ID it starts.
     * A replay of that exact transaction is accepted, while a second transaction
     * under the same authorization is rejected until the charger re-authorizes.
     */
    public boolean consumeForStart(String chargePointId, Integer connectorId, String idTag, Integer transactionId) {
        if (transactionId == null) {
            missing.increment();
            return false;
        }

        String bindingKey = startBindingKey(chargePointId, connectorId, idTag);
        if (bindingKey == null) {
            missing.increment();
            return false;
        }

        try {
            String binding = redisTemplate.opsForValue().get(bindingKey);
            if (matchesTransaction(binding, connectorId, transactionId)) {
                replayed.increment();
                return true;
            }

            String grantId = activeGrant(chargePointId, connectorId, idTag);
            if (grantId == null) {
                missing.increment();
                return false;
            }

            if (binding != null && binding.startsWith(grantId + ':')) {
                // The same physical authorization may start one transaction only.
                rejected.increment();
                return false;
            }

            redisTemplate.opsForValue().set(
                    bindingKey,
                    bindingValue(grantId, connectorId, transactionId),
                    grantTtl
            );
            consumed.increment();
            return true;
        } catch (DataAccessException exception) {
            redisFailures.increment();
            log.warn("Unable to validate OCPP authorization grant", exception);
            return false;
        }
    }

    /** Returns the session/start-attempt correlation carried by a remote-start grant. */
    public String correlationForStart(String chargePointId, String idTag) {
        try {
            String binding = redisTemplate.opsForValue().get(startBindingKey(chargePointId, null, idTag));
            if (binding == null) return null;
            int marker = binding.indexOf('|');
            if (marker < 0) return null;
            int connectorSeparator = binding.lastIndexOf(':');
            if (connectorSeparator < marker) return null;
            int previousSeparator = binding.lastIndexOf(':', connectorSeparator - 1);
            if (previousSeparator < marker) return null;
            String correlation = binding.substring(marker + 1, previousSeparator);
            return correlation.isBlank() ? null : correlation;
        } catch (DataAccessException exception) {
            redisFailures.increment();
            return null;
        }
    }

    private boolean store(String key) {
        return store(key, null);
    }

    private boolean store(String key, String correlationId) {
        if (key == null) {
            return false;
        }
        try {
            String value = UUID.randomUUID().toString();
            if (correlationId != null && !correlationId.isBlank()) value += "|" + correlationId.trim();
            redisTemplate.opsForValue().set(key, value, grantTtl);
            granted.increment();
            return true;
        } catch (DataAccessException exception) {
            redisFailures.increment();
            log.warn("Unable to persist OCPP authorization grant", exception);
            return false;
        }
    }

    private String activeGrant(String chargePointId, Integer connectorId, String idTag) {
        String remoteGrant = read(remoteStartKey(chargePointId, connectorId, idTag));
        return remoteGrant == null ? read(authorizationKey(chargePointId, idTag)) : remoteGrant;
    }

    private String read(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    private boolean matchesTransaction(String binding, Integer connectorId, Integer transactionId) {
        return binding != null && binding.endsWith(':' + String.valueOf(normalizedConnector(connectorId)) + ':' + transactionId);
    }

    private String bindingValue(String grantId, Integer connectorId, Integer transactionId) {
        return grantId + ':' + normalizedConnector(connectorId) + ':' + transactionId;
    }

    private String remoteStartKey(String chargePointId, Integer connectorId, String idTag) {
        String tokenHash = tokenHash(idTag);
        if (tokenHash == null) {
            return null;
        }
        return PREFIX + "remote:" + safeChargePointId(chargePointId) + ':' + normalizedConnector(connectorId) + ':' + tokenHash;
    }

    private String authorizationKey(String chargePointId, String idTag) {
        String tokenHash = tokenHash(idTag);
        if (tokenHash == null) {
            return null;
        }
        return PREFIX + "authorize:" + safeChargePointId(chargePointId) + ':' + tokenHash;
    }

    private String startBindingKey(String chargePointId, Integer connectorId, String idTag) {
        String tokenHash = tokenHash(idTag);
        if (tokenHash == null) {
            return null;
        }
        return PREFIX + "start:" + safeChargePointId(chargePointId) + ':' + tokenHash;
    }

    private String tokenHash(String idTag) {
        if (idTag == null || idTag.isBlank()) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(idTag.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String safeChargePointId(String chargePointId) {
        return chargePointId == null || chargePointId.isBlank() ? "unknown" : chargePointId.trim();
    }

    private int normalizedConnector(Integer connectorId) {
        return connectorId == null ? 0 : connectorId;
    }
}
