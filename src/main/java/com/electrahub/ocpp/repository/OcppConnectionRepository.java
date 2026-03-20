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

    Optional<OcppConnection> findByChargePointIdAndActiveTrue(String chargePointId);

    List<OcppConnection> findAllByActiveTrue();

    Optional<OcppConnection> findByChargePointId(String chargePointId);

    Page<OcppConnection> findByChargePointId(String chargePointId, Pageable pageable);

    long countByActiveTrue();

}
