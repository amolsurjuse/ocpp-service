package com.electrahub.ocpp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(
        name = "ocpp_charger_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ocpp_charger_credentials_charge_point",
                columnNames = "charge_point_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcppChargerCredential {

    public enum Status {
        ACTIVE,
        DISABLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "charge_point_id", nullable = false, length = 255)
    private String chargePointId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "previous_password_hash", length = 100)
    private String previousPasswordHash;

    @Column(name = "previous_valid_until")
    private Instant previousValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;
}
