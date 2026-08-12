package com.electrahub.ocpp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.electrahub.ocpp.domain.OcppChargerCredential;
import com.electrahub.ocpp.domain.OcppChargerCredential.Status;
import com.electrahub.ocpp.repository.OcppChargerCredentialAuditRepository;
import com.electrahub.ocpp.repository.OcppChargerCredentialRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class OcppChargerCredentialServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void createsOnlyAnAdaptiveHashAndNeverReturnsSecretMaterial() {
        OcppChargerCredentialRepository repository = mock(OcppChargerCredentialRepository.class);
        OcppChargerCredentialAuditRepository audits = mock(OcppChargerCredentialAuditRepository.class);
        when(repository.findByChargePointId("EH-IN-001")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            OcppChargerCredential value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
        OcppChargerCredentialService service = service(repository, audits);

        OcppChargerCredentialService.CredentialMetadata metadata = service.put(
                "EH-IN-001", "production-test-secret-001", null, Duration.ZERO, "operator@example.com");

        ArgumentCaptor<OcppChargerCredential> saved = ArgumentCaptor.forClass(OcppChargerCredential.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).startsWith("$2");
        assertThat(saved.getValue().getPasswordHash()).doesNotContain("production-test-secret-001");
        assertThat(metadata.toString()).doesNotContain("secret").doesNotContain("$2");
        verify(audits).save(any());
    }

    @Test
    void rotationAcceptsCurrentAndPreviousPasswordsOnlyDuringTheBoundedOverlap() {
        OcppChargerCredentialRepository repository = mock(OcppChargerCredentialRepository.class);
        OcppChargerCredentialAuditRepository audits = mock(OcppChargerCredentialAuditRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        OcppChargerCredential credential = credential(encoder.encode("old-production-secret"));
        when(repository.findByChargePointId("EH-IN-001")).thenReturn(Optional.of(credential));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OcppChargerCredentialService service = new OcppChargerCredentialService(
                repository, audits, encoder, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));

        service.put("EH-IN-001", "new-production-secret", null, Duration.ofHours(8), "rotation-job");

        assertThat(service.verify("EH-IN-001", "new-production-secret", NOW.plus(Duration.ofMinutes(30))))
                .isEqualTo(ChargerCredentialVerifier.Decision.VALID);
        assertThat(service.verify("EH-IN-001", "old-production-secret", NOW.plus(Duration.ofMinutes(30))))
                .isEqualTo(ChargerCredentialVerifier.Decision.VALID);
        assertThat(service.verify("EH-IN-001", "old-production-secret", NOW.plus(Duration.ofHours(2))))
                .isEqualTo(ChargerCredentialVerifier.Decision.INVALID);
        assertThat(credential.getPreviousValidUntil()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void disabledAndExpiredCredentialsFailClosed() {
        OcppChargerCredentialRepository repository = mock(OcppChargerCredentialRepository.class);
        OcppChargerCredentialAuditRepository audits = mock(OcppChargerCredentialAuditRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        OcppChargerCredential credential = credential(encoder.encode("production-test-secret"));
        when(repository.findByChargePointId("EH-IN-001")).thenReturn(Optional.of(credential));
        OcppChargerCredentialService service = new OcppChargerCredentialService(
                repository, audits, encoder, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));

        credential.setValidUntil(NOW);
        assertThat(service.verify("EH-IN-001", "production-test-secret", NOW))
                .isEqualTo(ChargerCredentialVerifier.Decision.EXPIRED);
        credential.setValidUntil(null);
        credential.setStatus(Status.DISABLED);
        assertThat(service.verify("EH-IN-001", "production-test-secret", NOW))
                .isEqualTo(ChargerCredentialVerifier.Decision.DISABLED);
    }

    private OcppChargerCredentialService service(
            OcppChargerCredentialRepository repository,
            OcppChargerCredentialAuditRepository audits
    ) {
        return new OcppChargerCredentialService(
                repository, audits, new BCryptPasswordEncoder(4),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1));
    }

    private OcppChargerCredential credential(String passwordHash) {
        return OcppChargerCredential.builder()
                .id(UUID.randomUUID())
                .chargePointId("EH-IN-001")
                .passwordHash(passwordHash)
                .status(Status.ACTIVE)
                .validFrom(NOW.minusSeconds(60))
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .build();
    }
}
