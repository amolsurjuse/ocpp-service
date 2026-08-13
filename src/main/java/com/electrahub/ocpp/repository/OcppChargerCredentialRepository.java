package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppChargerCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcppChargerCredentialRepository extends JpaRepository<OcppChargerCredential, UUID> {
    Optional<OcppChargerCredential> findByChargePointId(String chargePointId);
    long countByStatus(OcppChargerCredential.Status status);
}
