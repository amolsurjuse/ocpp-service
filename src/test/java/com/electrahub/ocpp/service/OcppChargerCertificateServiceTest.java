package com.electrahub.ocpp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.electrahub.ocpp.domain.OcppChargerCertificate;
import com.electrahub.ocpp.domain.OcppChargerCertificate.Status;
import com.electrahub.ocpp.repository.OcppChargerCertificateAuditRepository;
import com.electrahub.ocpp.repository.OcppChargerCertificateRepository;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;

class OcppChargerCertificateServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void registersPublicMetadataAndNeverRequiresPrivateKeyMaterial() throws Exception {
        OcppChargerCertificateRepository repository = mock(OcppChargerCertificateRepository.class);
        OcppChargerCertificateAuditRepository audits = mock(OcppChargerCertificateAuditRepository.class);
        when(repository.findByFingerprintSha256(any())).thenReturn(Optional.empty());
        when(repository.findAllByChargePointIdAndStatusIn(any(), any())).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        X509Certificate certificate = certificate("EH-TEST-001", new byte[]{1, 2, 3});

        OcppChargerCertificateService.CertificateMetadata result = service(repository, audits)
                .register("EH-TEST-001", certificate, Duration.ZERO, "pki-operator");

        assertThat(result.chargePointId()).isEqualTo("EH-TEST-001");
        assertThat(result.publicKeyAlgorithm()).isEqualTo("RSA-2048");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(audits).save(any());
    }

    @Test
    void rotationKeepsThePreviousFingerprintOnlyForTheBoundedOverlap() throws Exception {
        OcppChargerCertificateRepository repository = mock(OcppChargerCertificateRepository.class);
        OcppChargerCertificateAuditRepository audits = mock(OcppChargerCertificateAuditRepository.class);
        OcppChargerCertificate current = OcppChargerCertificate.builder()
                .chargePointId("EH-TEST-001").fingerprintSha256("a".repeat(64))
                .status(Status.ACTIVE).createdAt(NOW.minusSeconds(60)).updatedAt(NOW.minusSeconds(60)).build();
        when(repository.findByFingerprintSha256(any())).thenReturn(Optional.empty());
        when(repository.findAllByChargePointIdAndStatusIn(any(), any())).thenReturn(List.of(current));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service(repository, audits).register("EH-TEST-001",
                certificate("EH-TEST-001", new byte[]{4, 5, 6}), Duration.ofHours(1), "pki-operator");

        assertThat(current.getStatus()).isEqualTo(Status.RETIRING);
        assertThat(current.getRetireAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void rejectsCertificateWhoseSubjectDoesNotMatchTheChargerPath() throws Exception {
        OcppChargerCertificateRepository repository = mock(OcppChargerCertificateRepository.class);
        OcppChargerCertificateAuditRepository audits = mock(OcppChargerCertificateAuditRepository.class);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service(repository, audits)
                .register("EH-TEST-001", certificate("OTHER", new byte[]{7}), Duration.ZERO, "operator")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IDENTITY_MISMATCH");
    }

    @Test
    void revokedFingerprintIsNotAdmitted() throws Exception {
        OcppChargerCertificateRepository repository = mock(OcppChargerCertificateRepository.class);
        OcppChargerCertificateAuditRepository audits = mock(OcppChargerCertificateAuditRepository.class);
        X509Certificate certificate = certificate("EH-TEST-001", new byte[]{9, 9});
        OcppChargerCertificate registered = OcppChargerCertificate.builder()
                .chargePointId("EH-TEST-001")
                .fingerprintSha256(OcppChargerCertificateService.fingerprint(certificate))
                .status(Status.REVOKED).validFrom(NOW.minusSeconds(60)).validUntil(NOW.plusSeconds(3600)).build();
        when(repository.findByFingerprintSha256(any())).thenReturn(Optional.of(registered));

        assertThat(service(repository, audits).verify("EH-TEST-001", certificate, NOW))
                .isEqualTo(ChargerCertificateVerifier.Decision.INACTIVE);
    }

    @Test
    void fleetReadinessFailsClosedWhenAnyKnownChargerIsMissingACertificate() {
        OcppChargerCertificateRepository repository = mock(OcppChargerCertificateRepository.class);
        OcppChargerCertificateAuditRepository audits = mock(OcppChargerCertificateAuditRepository.class);
        OcppConnectionRepository connections = mock(OcppConnectionRepository.class);
        when(connections.count()).thenReturn(100L);
        when(repository.countDistinctChargePointIdsByStatusIn(any())).thenReturn(99L);
        when(repository.countByStatus(Status.ACTIVE)).thenReturn(99L);
        when(repository.countByStatus(Status.RETIRING)).thenReturn(0L);
        when(repository.countByStatus(Status.REVOKED)).thenReturn(1L);
        when(repository.countByStatusInAndValidUntilBefore(any(), any())).thenReturn(0L);

        var result = service(repository, audits, connections).fleetReadiness(Duration.ofDays(30));

        assertThat(result.missingCertificates()).isEqualTo(1);
        assertThat(result.enforcementReady()).isFalse();
    }

    private OcppChargerCertificateService service(
            OcppChargerCertificateRepository repository,
            OcppChargerCertificateAuditRepository audits
    ) {
        return service(repository, audits, mock(OcppConnectionRepository.class));
    }

    private OcppChargerCertificateService service(
            OcppChargerCertificateRepository repository,
            OcppChargerCertificateAuditRepository audits,
            OcppConnectionRepository connections
    ) {
        return new OcppChargerCertificateService(repository, audits, connections,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24));
    }

    private X509Certificate certificate(String commonName, byte[] encoded) throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        RSAPublicKey key = mock(RSAPublicKey.class);
        when(key.getModulus()).thenReturn(BigInteger.ONE.shiftLeft(2047));
        when(certificate.getPublicKey()).thenReturn(key);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=" + commonName));
        when(certificate.getIssuerX500Principal()).thenReturn(new X500Principal("CN=ElectraHub Test CA"));
        when(certificate.getSubjectAlternativeNames()).thenReturn(null);
        when(certificate.getNotBefore()).thenReturn(Date.from(NOW.minus(Duration.ofDays(1))));
        when(certificate.getNotAfter()).thenReturn(Date.from(NOW.plus(Duration.ofDays(30))));
        when(certificate.getSerialNumber()).thenReturn(BigInteger.valueOf(42));
        when(certificate.getEncoded()).thenReturn(encoded);
        return certificate;
    }
}
