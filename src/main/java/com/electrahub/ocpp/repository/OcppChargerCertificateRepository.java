package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppChargerCertificate;
import com.electrahub.ocpp.domain.OcppChargerCertificate.Status;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OcppChargerCertificateRepository extends JpaRepository<OcppChargerCertificate, UUID> {
    Optional<OcppChargerCertificate> findByFingerprintSha256(String fingerprintSha256);
    List<OcppChargerCertificate> findAllByChargePointIdOrderByCreatedAtDesc(String chargePointId);
    List<OcppChargerCertificate> findAllByChargePointIdAndStatusIn(
            String chargePointId, Collection<Status> statuses);

    @Query("select count(distinct c.chargePointId) from OcppChargerCertificate c where c.status in :statuses")
    long countDistinctChargePointIdsByStatusIn(@Param("statuses") Collection<Status> statuses);

    long countByStatus(Status status);

    long countByStatusInAndValidUntilBefore(Collection<Status> statuses, java.time.Instant threshold);
}
