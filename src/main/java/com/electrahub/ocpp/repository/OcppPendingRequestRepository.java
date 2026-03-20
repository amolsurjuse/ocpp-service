package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppPendingRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcppPendingRequestRepository extends JpaRepository<OcppPendingRequest, UUID> {

    Optional<OcppPendingRequest> findByMessageId(String messageId);

    List<OcppPendingRequest> findByChargePointIdAndRespondedFalse(String chargePointId);

    long deleteByRespondedTrueAndRespondedAtBefore(Instant instant);

}
