package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppMessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcppMessageLogRepository extends JpaRepository<OcppMessageLog, UUID> {

    Page<OcppMessageLog> findByChargePointIdOrderByCreatedAtDesc(String chargePointId, Pageable pageable);

    Optional<OcppMessageLog> findByMessageId(String messageId);

    long countByChargePointIdAndDirection(String chargePointId, String direction);

}
