package com.electrahub.ocpp.repository;

import com.electrahub.ocpp.domain.OcppChargerCredentialAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcppChargerCredentialAuditRepository extends JpaRepository<OcppChargerCredentialAudit, UUID> {
}
