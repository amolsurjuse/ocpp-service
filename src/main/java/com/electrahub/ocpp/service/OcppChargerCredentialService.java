package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppChargerCredential;
import com.electrahub.ocpp.domain.OcppChargerCredential.Status;
import com.electrahub.ocpp.domain.OcppChargerCredentialAudit;
import com.electrahub.ocpp.repository.OcppChargerCredentialAuditRepository;
import com.electrahub.ocpp.repository.OcppChargerCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OcppChargerCredentialService implements ChargerCredentialVerifier {

    private static final int MIN_PASSWORD_CHARACTERS = 16;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final OcppChargerCredentialRepository repository;
    private final OcppChargerCredentialAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration maximumOverlap;

    @Autowired
    public OcppChargerCredentialService(
            OcppChargerCredentialRepository repository,
            OcppChargerCredentialAuditRepository auditRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ocpp.handshake.credential-max-overlap-seconds:86400}") long maximumOverlapSeconds
    ) {
        this(repository, auditRepository, passwordEncoder, Clock.systemUTC(),
                Duration.ofSeconds(Math.max(0, maximumOverlapSeconds)));
    }

    OcppChargerCredentialService(
            OcppChargerCredentialRepository repository,
            OcppChargerCredentialAuditRepository auditRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            Duration maximumOverlap
    ) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.maximumOverlap = maximumOverlap;
    }

    @Transactional
    public CredentialMetadata put(
            String rawChargePointId,
            String password,
            Instant validUntil,
            Duration requestedOverlap,
            String rawActor
    ) {
        String chargePointId = normalizeChargePointId(rawChargePointId);
        validatePassword(password);
        Instant now = clock.instant();
        if (validUntil != null && !validUntil.isAfter(now)) {
            throw new IllegalArgumentException("validUntil must be in the future");
        }
        Duration overlap = boundedOverlap(requestedOverlap);

        OcppChargerCredential credential = repository.findByChargePointId(chargePointId)
                .orElseGet(() -> OcppChargerCredential.builder()
                        .chargePointId(chargePointId)
                        .status(Status.ACTIVE)
                        .validFrom(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
        boolean rotating = credential.getId() != null;
        if (rotating && !overlap.isZero()) {
            credential.setPreviousPasswordHash(credential.getPasswordHash());
            credential.setPreviousValidUntil(now.plus(overlap));
        } else {
            credential.setPreviousPasswordHash(null);
            credential.setPreviousValidUntil(null);
        }
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setStatus(Status.ACTIVE);
        credential.setValidFrom(now);
        credential.setValidUntil(validUntil);
        credential.setUpdatedAt(now);
        credential = repository.saveAndFlush(credential);
        audit(credential, rotating ? "ROTATED" : "CREATED", rawActor, now);
        return metadata(credential);
    }

    @Transactional
    public CredentialMetadata disable(String rawChargePointId, String rawActor) {
        String chargePointId = normalizeChargePointId(rawChargePointId);
        OcppChargerCredential credential = repository.findByChargePointId(chargePointId)
                .orElseThrow(() -> new IllegalArgumentException("Charger credential was not found"));
        Instant now = clock.instant();
        credential.setStatus(Status.DISABLED);
        credential.setPreviousPasswordHash(null);
        credential.setPreviousValidUntil(null);
        credential.setUpdatedAt(now);
        credential = repository.saveAndFlush(credential);
        audit(credential, "DISABLED", rawActor, now);
        return metadata(credential);
    }

    @Transactional(readOnly = true)
    public CredentialMetadata metadata(String rawChargePointId) {
        return repository.findByChargePointId(normalizeChargePointId(rawChargePointId))
                .map(this::metadata)
                .orElseThrow(() -> new IllegalArgumentException("Charger credential was not found"));
    }

    @Override
    public Decision verify(String rawChargePointId, String password, Instant now) {
        if (password == null || now == null) {
            return Decision.INVALID;
        }
        String chargePointId;
        try {
            chargePointId = normalizeChargePointId(rawChargePointId);
        } catch (IllegalArgumentException exception) {
            return Decision.INVALID;
        }
        // Spring Data completes its short read-only repository transaction
        // before BCrypt runs. Holding a JDBC connection during an expensive
        // hash comparison exhausts the pool during fleet reconnect bursts.
        OcppChargerCredential credential = repository.findByChargePointId(chargePointId).orElse(null);
        if (credential == null) {
            return Decision.NOT_FOUND;
        }
        if (credential.getStatus() != Status.ACTIVE) {
            return Decision.DISABLED;
        }
        if (credential.getValidFrom() != null && credential.getValidFrom().isAfter(now)) {
            return Decision.NOT_YET_VALID;
        }
        if (credential.getValidUntil() != null && !credential.getValidUntil().isAfter(now)) {
            return Decision.EXPIRED;
        }
        if (safeMatches(password, credential.getPasswordHash())) {
            return Decision.VALID;
        }
        if (credential.getPreviousPasswordHash() != null
                && credential.getPreviousValidUntil() != null
                && credential.getPreviousValidUntil().isAfter(now)
                && safeMatches(password, credential.getPreviousPasswordHash())) {
            return Decision.VALID;
        }
        return Decision.INVALID;
    }

    private boolean safeMatches(String password, String hash) {
        try {
            return hash != null && passwordEncoder.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void audit(OcppChargerCredential credential, String action, String actor, Instant now) {
        auditRepository.save(OcppChargerCredentialAudit.builder()
                .chargePointId(credential.getChargePointId())
                .action(action)
                .actor(normalizeActor(actor))
                .credentialVersion(credential.getCredentialVersion())
                .createdAt(now)
                .build());
    }

    private CredentialMetadata metadata(OcppChargerCredential credential) {
        return new CredentialMetadata(
                credential.getChargePointId(),
                credential.getStatus().name(),
                credential.getCredentialVersion(),
                credential.getValidFrom(),
                credential.getValidUntil(),
                credential.getPreviousValidUntil(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }

    private Duration boundedOverlap(Duration requested) {
        if (requested == null || requested.isNegative() || requested.isZero()) {
            return Duration.ZERO;
        }
        return requested.compareTo(maximumOverlap) > 0 ? maximumOverlap : requested;
    }

    private static String normalizeChargePointId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException("A valid chargePointId is required");
        }
        return normalized;
    }

    private static String normalizeActor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "internal-service";
        }
        return normalized.substring(0, Math.min(120, normalized.length()));
    }

    private static void validatePassword(String password) {
        int byteLength = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (password == null || password.length() < MIN_PASSWORD_CHARACTERS || byteLength > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Credential password must contain at least 16 characters and no more than 72 UTF-8 bytes");
        }
    }

    public record CredentialMetadata(
            String chargePointId,
            String status,
            long credentialVersion,
            Instant validFrom,
            Instant validUntil,
            Instant previousValidUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
