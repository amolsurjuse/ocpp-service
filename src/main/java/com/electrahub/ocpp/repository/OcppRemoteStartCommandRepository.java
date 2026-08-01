package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppRemoteStartCommand;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandOutcome;
import com.electrahub.ocpp.domain.enums.RemoteStartCommandState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcppRemoteStartCommandRepository extends JpaRepository<OcppRemoteStartCommand, UUID> {

    Optional<OcppRemoteStartCommand> findByCommandKey(String commandKey);

    Optional<OcppRemoteStartCommand> findByChargePointIdAndRemoteStartId(
            String chargePointId,
            Integer remoteStartId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update OcppRemoteStartCommand command
               set command.state = :state,
                   command.outcome = :outcome,
                   command.responsePayload = :responsePayload,
                   command.failureReason = :failureReason,
                   command.updatedAt = :updatedAt,
                   command.completedAt = :completedAt
             where command.id = :id
               and command.state = :pendingState
            """)
    int transitionPending(
            @Param("id") UUID id,
            @Param("pendingState") RemoteStartCommandState pendingState,
            @Param("state") RemoteStartCommandState state,
            @Param("outcome") RemoteStartCommandOutcome outcome,
            @Param("responsePayload") String responsePayload,
            @Param("failureReason") String failureReason,
            @Param("updatedAt") Instant updatedAt,
            @Param("completedAt") Instant completedAt
    );
}
