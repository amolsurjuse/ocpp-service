package com.electrahub.ocpp.domain;

import com.electrahub.ocpp.domain.enums.RemoteStartCommandOutcome;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandState;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ocpp_remote_start_commands",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ocpp_remote_start_command_key",
                        columnNames = "command_key"
                ),
                @UniqueConstraint(
                        name = "uk_ocpp_remote_start_charge_point_id",
                        columnNames = {"charge_point_id", "remote_start_id"}
                )
        },
        indexes = {
                @Index(name = "idx_remote_start_state_deadline", columnList = "state,deadline_at"),
                @Index(name = "idx_remote_start_charge_point_created", columnList = "charge_point_id,created_at")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcppRemoteStartCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "command_key", nullable = false, updatable = false, length = 200)
    private String commandKey;

    @Column(name = "correlation_id", updatable = false, length = 200)
    private String correlationId;

    @Column(name = "charge_point_id", nullable = false, updatable = false)
    private String chargePointId;

    @Column(name = "id_tag_fingerprint", nullable = false, updatable = false, length = 64)
    private String idTagFingerprint;

    @Column(name = "connector_id", updatable = false)
    private Integer connectorId;

    @Column(nullable = false, updatable = false, length = 32)
    private String protocol;

    @Column(name = "message_id", nullable = false, updatable = false, unique = true, length = 64)
    private String messageId;

    @Column(name = "remote_start_id", updatable = false)
    private Integer remoteStartId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RemoteStartCommandState state;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RemoteStartCommandOutcome outcome;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deadline_at", nullable = false, updatable = false)
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
