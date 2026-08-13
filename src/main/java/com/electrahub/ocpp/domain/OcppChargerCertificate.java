package com.electrahub.ocpp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ocpp_charger_certificates", indexes = {
        @Index(name = "idx_ocpp_charger_certificate_charge_point_status", columnList = "charge_point_id,status")
}, uniqueConstraints = @UniqueConstraint(
        name = "uk_ocpp_charger_certificate_fingerprint", columnNames = "fingerprint_sha256"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcppChargerCertificate {

    public enum Status {
        ACTIVE,
        RETIRING,
        RETIRED,
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "charge_point_id", nullable = false, length = 255)
    private String chargePointId;

    @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 64)
    private String fingerprintSha256;

    @Column(name = "serial_number", nullable = false, length = 128)
    private String serialNumber;

    @Column(name = "subject_dn", nullable = false, length = 1000)
    private String subjectDn;

    @Column(name = "issuer_dn", nullable = false, length = 1000)
    private String issuerDn;

    @Column(name = "public_key_algorithm", nullable = false, length = 32)
    private String publicKeyAlgorithm;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "retire_at")
    private Instant retireAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "certificate_version", nullable = false)
    private long certificateVersion;
}
