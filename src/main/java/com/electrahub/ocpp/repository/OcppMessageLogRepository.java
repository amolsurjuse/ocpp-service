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

    /**
     * Retrieves find by charge point id order by created at desc for `OcppMessageLogRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param chargePointId input consumed by findByChargePointIdOrderByCreatedAtDesc.
     * @param pageable input consumed by findByChargePointIdOrderByCreatedAtDesc.
     * @return result produced by findByChargePointIdOrderByCreatedAtDesc.
     */
    Page<OcppMessageLog> findByChargePointIdOrderByCreatedAtDesc(String chargePointId, Pageable pageable);

    /**
     * Retrieves find by message id for `OcppMessageLogRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param messageId input consumed by findByMessageId.
     * @return result produced by findByMessageId.
     */
    Optional<OcppMessageLog> findByMessageId(String messageId);

    /**
     * Executes count by charge point id and direction for `OcppMessageLogRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param chargePointId input consumed by countByChargePointIdAndDirection.
     * @param direction input consumed by countByChargePointIdAndDirection.
     * @return result produced by countByChargePointIdAndDirection.
     */
    long countByChargePointIdAndDirection(String chargePointId, String direction);

}
