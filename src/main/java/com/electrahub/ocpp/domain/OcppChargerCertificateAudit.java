package com.electrahub.ocpp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ocpp_charger_certificate_audit", indexes = {
        @Index(name = "idx_ocpp_charger_certificate_audit_created", columnList = "charge_point_id,created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcppChargerCertificateAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "charge_point_id", nullable = false, length = 255)
    private String chargePointId;

    @Column(name = "fingerprint_sha256", nullable = false, length = 64)
    private String fingerprintSha256;

    @Column(nullable = false, length = 24)
    private String action;

    @Column(nullable = false, length = 120)
    private String actor;

    @Column(name = "certificate_version", nullable = false)
    private long certificateVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
