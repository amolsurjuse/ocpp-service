package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppChargerCertificate;
import com.electrahub.ocpp.domain.OcppChargerCertificate.Status;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcppChargerCertificateRepository extends JpaRepository<OcppChargerCertificate, UUID> {
    Optional<OcppChargerCertificate> findByFingerprintSha256(String fingerprintSha256);
    List<OcppChargerCertificate> findAllByChargePointIdOrderByCreatedAtDesc(String chargePointId);
    List<OcppChargerCertificate> findAllByChargePointIdAndStatusIn(
            String chargePointId, Collection<Status> statuses);
}
