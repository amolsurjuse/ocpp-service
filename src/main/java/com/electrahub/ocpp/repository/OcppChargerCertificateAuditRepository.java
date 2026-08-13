package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppChargerCertificateAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcppChargerCertificateAuditRepository
        extends JpaRepository<OcppChargerCertificateAudit, UUID> {
}
