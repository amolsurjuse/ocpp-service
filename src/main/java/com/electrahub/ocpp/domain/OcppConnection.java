package com.electrahub.ocpp.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ocpp_connections", indexes = {
    @Index(name = "idx_charge_point_id", columnList = "charge_point_id"),
    @Index(name = "idx_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcppConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String chargePointId;

    @Column
    private String sessionId;

    @Column
    private String nodeId;

    @Column
    private String ocppProtocol;

    @Column(nullable = false)
    private Instant connectedAt;

    @Column
    private Instant lastHeartbeatAt;

    @Column
    private Instant disconnectedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

}
