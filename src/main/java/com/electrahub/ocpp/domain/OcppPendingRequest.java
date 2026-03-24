package com.electrahub.ocpp.domain;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ocpp_pending_requests", indexes = {
    @Index(name = "idx_message_id_pending", columnList = "message_id"),
    @Index(name = "idx_charge_point_responded", columnList = "charge_point_id,responded")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcppPendingRequest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppPendingRequest.class);


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String chargePointId;

    @Column(nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private Instant timeoutAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean responded = false;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column
    private Instant respondedAt;

}
