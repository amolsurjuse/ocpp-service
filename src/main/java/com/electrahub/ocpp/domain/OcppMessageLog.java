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
@Table(name = "ocpp_message_log", indexes = {
    @Index(name = "idx_charge_point_id_msg", columnList = "charge_point_id"),
    @Index(name = "idx_message_id_log", columnList = "message_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcppMessageLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppMessageLog.class);


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String chargePointId;

    @Column(nullable = false)
    private String messageId;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    @Column
    private String status;

    @Column
    private String errorCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant respondedAt;

}
