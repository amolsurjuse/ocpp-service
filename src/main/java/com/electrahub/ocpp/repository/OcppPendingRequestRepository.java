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

    /**
     * Retrieves find by message id for `OcppPendingRequestRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param messageId input consumed by findByMessageId.
     * @return result produced by findByMessageId.
     */
    Optional<OcppPendingRequest> findByMessageId(String messageId);

    /**
     * Retrieves find by charge point id and responded false for `OcppPendingRequestRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param chargePointId input consumed by findByChargePointIdAndRespondedFalse.
     * @return result produced by findByChargePointIdAndRespondedFalse.
     */
    List<OcppPendingRequest> findByChargePointIdAndRespondedFalse(String chargePointId);

    /**
     * Removes delete by responded true and responded at before for `OcppPendingRequestRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param instant input consumed by deleteByRespondedTrueAndRespondedAtBefore.
     * @return result produced by deleteByRespondedTrueAndRespondedAtBefore.
     */
    long deleteByRespondedTrueAndRespondedAtBefore(Instant instant);

}
