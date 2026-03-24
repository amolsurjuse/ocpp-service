package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcppConnectionRepository extends JpaRepository<OcppConnection, UUID> {

    /**
     * Retrieves find by charge point id and active true for `OcppConnectionRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param chargePointId input consumed by findByChargePointIdAndActiveTrue.
     * @return result produced by findByChargePointIdAndActiveTrue.
     */
    Optional<OcppConnection> findByChargePointIdAndActiveTrue(String chargePointId);

    /**
     * Retrieves find all by active true for `OcppConnectionRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @return result produced by findAllByActiveTrue.
     */
    List<OcppConnection> findAllByActiveTrue();

    /**
     * Retrieves find by charge point id for `OcppConnectionRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param chargePointId input consumed by findByChargePointId.
     * @return result produced by findByChargePointId.
     */
    Optional<OcppConnection> findByChargePointId(String chargePointId);

    /**
     * Retrieves find by charge point id for `OcppConnectionRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @param chargePointId input consumed by findByChargePointId.
     * @param pageable input consumed by findByChargePointId.
     * @return result produced by findByChargePointId.
     */
    Page<OcppConnection> findByChargePointId(String chargePointId, Pageable pageable);

    /**
     * Executes count by active true for `OcppConnectionRepository`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.repository`.
     * @return result produced by countByActiveTrue.
     */
    long countByActiveTrue();

}
