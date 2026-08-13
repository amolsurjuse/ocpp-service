package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppChargerCertificate;
import com.electrahub.ocpp.domain.OcppChargerCertificate.Status;
import com.electrahub.ocpp.domain.OcppChargerCertificateAudit;
import com.electrahub.ocpp.domain.OcppChargerCredential;
import com.electrahub.ocpp.repository.OcppChargerCertificateAuditRepository;
import com.electrahub.ocpp.repository.OcppChargerCertificateRepository;
import com.electrahub.ocpp.repository.OcppChargerCredentialRepository;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OcppChargerCertificateService implements ChargerCertificateVerifier {

    private static final String CHARGER_URI_PREFIX = "urn:electrahub:charge-point:";
    private static final List<Status> ADMISSIBLE = List.of(Status.ACTIVE, Status.RETIRING);

    private final OcppChargerCertificateRepository repository;
    private final OcppChargerCertificateAuditRepository auditRepository;
    private final Clock clock;
    private final Duration maximumOverlap;
    private final OcppChargerCredentialRepository credentialRepository;

    @Autowired
    public OcppChargerCertificateService(
            OcppChargerCertificateRepository repository,
            OcppChargerCertificateAuditRepository auditRepository,
            OcppChargerCredentialRepository credentialRepository,
            @Value("${ocpp.mtls.certificate-max-overlap-seconds:${OCPP_MTLS_CERTIFICATE_MAX_OVERLAP_SECONDS:86400}}")
            long maximumOverlapSeconds
    ) {
        this(repository, auditRepository, credentialRepository, Clock.systemUTC(),
                Duration.ofSeconds(Math.max(0, maximumOverlapSeconds)));
    }

    OcppChargerCertificateService(
            OcppChargerCertificateRepository repository,
            OcppChargerCertificateAuditRepository auditRepository,
            OcppChargerCredentialRepository credentialRepository,
            Clock clock,
            Duration maximumOverlap
    ) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.clock = clock;
        this.maximumOverlap = maximumOverlap;
        this.credentialRepository = credentialRepository;
    }

    @Transactional
    public CertificateMetadata register(
            String rawChargePointId,
            X509Certificate certificate,
            Duration requestedOverlap,
            String rawActor
    ) {
        String chargePointId = normalizeChargePointId(rawChargePointId);
        Instant now = clock.instant();
        validateForRegistration(chargePointId, certificate, now);
        String fingerprint = fingerprint(certificate);
        OcppChargerCertificate existing = repository.findByFingerprintSha256(fingerprint).orElse(null);
        if (existing != null) {
            if (!MessageDigest.isEqual(
                    existing.getChargePointId().getBytes(StandardCharsets.UTF_8),
                    chargePointId.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Certificate is already bound to another charge point");
            }
            return metadata(existing);
        }

        Duration overlap = boundedOverlap(requestedOverlap);
        for (OcppChargerCertificate current : repository.findAllByChargePointIdAndStatusIn(
                chargePointId, ADMISSIBLE)) {
            current.setStatus(overlap.isZero() ? Status.RETIRED : Status.RETIRING);
            current.setRetireAt(overlap.isZero() ? now : now.plus(overlap));
            current.setUpdatedAt(now);
            OcppChargerCertificate saved = repository.saveAndFlush(current);
            audit(saved, overlap.isZero() ? "RETIRED" : "ROTATING", rawActor, now);
        }

        OcppChargerCertificate value = OcppChargerCertificate.builder()
                .chargePointId(chargePointId)
                .fingerprintSha256(fingerprint)
                .serialNumber(unsignedHex(certificate.getSerialNumber()))
                .subjectDn(certificate.getSubjectX500Principal().getName())
                .issuerDn(certificate.getIssuerX500Principal().getName())
                .publicKeyAlgorithm(keyDescription(certificate))
                .validFrom(certificate.getNotBefore().toInstant())
                .validUntil(certificate.getNotAfter().toInstant())
                .status(Status.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        value = repository.saveAndFlush(value);
        audit(value, "REGISTERED", rawActor, now);
        return metadata(value);
    }

    @Transactional
    public CertificateMetadata revoke(String rawChargePointId, String rawFingerprint, String rawActor) {
        String chargePointId = normalizeChargePointId(rawChargePointId);
        String fingerprint = normalizeFingerprint(rawFingerprint);
        OcppChargerCertificate value = repository.findByFingerprintSha256(fingerprint)
                .orElseThrow(() -> new IllegalArgumentException("Charger certificate was not found"));
        if (!value.getChargePointId().equals(chargePointId)) {
            throw new IllegalArgumentException("Certificate is not bound to this charge point");
        }
        Instant now = clock.instant();
        value.setStatus(Status.REVOKED);
        value.setRetireAt(now);
        value.setUpdatedAt(now);
        value = repository.saveAndFlush(value);
        audit(value, "REVOKED", rawActor, now);
        return metadata(value);
    }

    @Transactional(readOnly = true)
    public List<CertificateMetadata> metadata(String rawChargePointId) {
        return repository.findAllByChargePointIdOrderByCreatedAtDesc(normalizeChargePointId(rawChargePointId))
                .stream().map(this::metadata).toList();
    }

    @Transactional(readOnly = true)
    public FleetReadiness fleetReadiness(Duration expiryWarning) {
        Instant now = clock.instant();
        Duration warning = expiryWarning == null || expiryWarning.isNegative()
                ? Duration.ofDays(30) : expiryWarning;
        long knownFleet = credentialRepository.countByStatus(OcppChargerCredential.Status.ACTIVE);
        long enrolled = repository.countDistinctChargePointIdsByStatusIn(ADMISSIBLE);
        long active = repository.countByStatus(Status.ACTIVE);
        long retiring = repository.countByStatus(Status.RETIRING);
        long revoked = repository.countByStatus(Status.REVOKED);
        long expiring = repository.countByStatusInAndValidUntilBefore(ADMISSIBLE, now.plus(warning));
        long missing = Math.max(0, knownFleet - enrolled);
        boolean ready = knownFleet > 0 && missing == 0 && expiring == 0;
        return new FleetReadiness(now, knownFleet, enrolled, missing, active, retiring,
                revoked, expiring, ready);
    }

    @Override
    @Transactional(readOnly = true)
    public Decision verify(String rawChargePointId, X509Certificate certificate, Instant now) {
        if (certificate == null || now == null) {
            return Decision.MALFORMED;
        }
        String chargePointId;
        try {
            chargePointId = normalizeChargePointId(rawChargePointId);
        } catch (IllegalArgumentException exception) {
            return Decision.IDENTITY_MISMATCH;
        }
        if (!isApprovedKey(certificate)) {
            return Decision.WEAK_KEY;
        }
        if (!hasIdentity(certificate, chargePointId)) {
            return Decision.IDENTITY_MISMATCH;
        }
        if (certificate.getNotBefore().toInstant().isAfter(now)) {
            return Decision.NOT_YET_VALID;
        }
        if (!certificate.getNotAfter().toInstant().isAfter(now)) {
            return Decision.EXPIRED;
        }
        OcppChargerCertificate registered = repository.findByFingerprintSha256(fingerprint(certificate)).orElse(null);
        if (registered == null) {
            return Decision.UNREGISTERED;
        }
        if (!registered.getChargePointId().equals(chargePointId)) {
            return Decision.IDENTITY_MISMATCH;
        }
        if (registered.getStatus() == Status.ACTIVE) {
            return Decision.VALID;
        }
        if (registered.getStatus() == Status.RETIRING
                && registered.getRetireAt() != null
                && registered.getRetireAt().isAfter(now)) {
            return Decision.VALID;
        }
        return Decision.INACTIVE;
    }

    private void validateForRegistration(String chargePointId, X509Certificate certificate, Instant now) {
        Decision decision = verifyStatic(chargePointId, certificate, now);
        if (decision != Decision.VALID) {
            throw new IllegalArgumentException("Certificate failed validation: " + decision.name());
        }
    }

    private static Decision verifyStatic(String chargePointId, X509Certificate certificate, Instant now) {
        if (certificate == null) {
            return Decision.MALFORMED;
        }
        if (!isApprovedKey(certificate)) {
            return Decision.WEAK_KEY;
        }
        if (!hasIdentity(certificate, chargePointId)) {
            return Decision.IDENTITY_MISMATCH;
        }
        if (certificate.getNotBefore().toInstant().isAfter(now)) {
            return Decision.NOT_YET_VALID;
        }
        return certificate.getNotAfter().toInstant().isAfter(now) ? Decision.VALID : Decision.EXPIRED;
    }

    static boolean hasIdentity(X509Certificate certificate, String chargePointId) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names != null) {
                for (List<?> name : names) {
                    if (name.size() >= 2 && Integer.valueOf(6).equals(name.get(0))
                            && (CHARGER_URI_PREFIX + chargePointId).equals(name.get(1))) {
                        return true;
                    }
                }
            }
            LdapName subject = new LdapName(certificate.getSubjectX500Principal().getName());
            for (Rdn rdn : subject.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType()) && chargePointId.equals(String.valueOf(rdn.getValue()))) {
                    return true;
                }
            }
        } catch (Exception exception) {
            return false;
        }
        return false;
    }

    static boolean isApprovedKey(X509Certificate certificate) {
        if (certificate.getPublicKey() instanceof RSAPublicKey rsa) {
            return rsa.getModulus().bitLength() >= 2048;
        }
        if (certificate.getPublicKey() instanceof ECPublicKey ec) {
            try {
                AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
                parameters.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec approved = parameters.getParameterSpec(ECParameterSpec.class);
                ECParameterSpec supplied = ec.getParams();
                return supplied != null
                        && supplied.getCurve().equals(approved.getCurve())
                        && supplied.getGenerator().equals(approved.getGenerator())
                        && supplied.getOrder().equals(approved.getOrder())
                        && supplied.getCofactor() == approved.getCofactor();
            } catch (Exception exception) {
                return false;
            }
        }
        return false;
    }

    static String fingerprint(X509Certificate certificate) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (NoSuchAlgorithmException | CertificateEncodingException exception) {
            throw new IllegalArgumentException("Certificate fingerprint could not be calculated", exception);
        }
    }

    private static String keyDescription(X509Certificate certificate) {
        if (certificate.getPublicKey() instanceof RSAPublicKey rsa) {
            return "RSA-" + rsa.getModulus().bitLength();
        }
        if (certificate.getPublicKey() instanceof ECPublicKey) {
            return "EC-secp256r1";
        }
        return certificate.getPublicKey().getAlgorithm();
    }

    private void audit(OcppChargerCertificate certificate, String action, String actor, Instant now) {
        auditRepository.save(OcppChargerCertificateAudit.builder()
                .chargePointId(certificate.getChargePointId())
                .fingerprintSha256(certificate.getFingerprintSha256())
                .action(action)
                .actor(normalizeActor(actor))
                .certificateVersion(certificate.getCertificateVersion())
                .createdAt(now)
                .build());
    }

    private CertificateMetadata metadata(OcppChargerCertificate value) {
        return new CertificateMetadata(value.getChargePointId(), value.getFingerprintSha256(),
                value.getSerialNumber(), value.getSubjectDn(), value.getIssuerDn(), value.getPublicKeyAlgorithm(),
                value.getStatus().name(), value.getValidFrom(), value.getValidUntil(), value.getRetireAt(),
                value.getCertificateVersion(), value.getCreatedAt(), value.getUpdatedAt());
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

    private static String normalizeFingerprint(String value) {
        String normalized = value == null ? "" : value.replace(":", "").trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A valid SHA-256 certificate fingerprint is required");
        }
        return normalized;
    }

    private static String normalizeActor(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "internal-service"
                : normalized.substring(0, Math.min(120, normalized.length()));
    }

    private static String unsignedHex(BigInteger value) {
        return value == null ? "0" : value.toString(16);
    }

    public record CertificateMetadata(
            String chargePointId,
            String fingerprintSha256,
            String serialNumber,
            String subjectDn,
            String issuerDn,
            String publicKeyAlgorithm,
            String status,
            Instant validFrom,
            Instant validUntil,
            Instant retireAt,
            long certificateVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record FleetReadiness(
            Instant evaluatedAt,
            long knownFleet,
            long enrolledChargePoints,
            long missingCertificates,
            long activeCertificates,
            long retiringCertificates,
            long revokedCertificates,
            long certificatesExpiringWithinWarningWindow,
            boolean enforcementReady
    ) {}
}
