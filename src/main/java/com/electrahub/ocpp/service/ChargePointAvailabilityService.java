package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.integration.StationServiceClient;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.websocket.ConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class ChargePointAvailabilityService {

    private final ConnectionManager connectionManager;
    private final OcppConnectionRepository connectionRepository;
    private final StationServiceClient stationServiceClient;

    public ChargePointAvailabilityService(
            ConnectionManager connectionManager,
            OcppConnectionRepository connectionRepository,
            StationServiceClient stationServiceClient
    ) {
        this.connectionManager = connectionManager;
        this.connectionRepository = connectionRepository;
        this.stationServiceClient = stationServiceClient;
    }

    public void markConnected(String chargePointId) {
        if (chargePointId == null || chargePointId.isBlank() || "unknown".equalsIgnoreCase(chargePointId)) {
            return;
        }
        stationServiceClient.markChargePointOnline(chargePointId);
    }

    public void markOffline(OcppConnection connection, String reason) {
        if (connection == null) {
            return;
        }
        markOffline(connection.getChargePointId(), reason);
    }

    public void markOffline(String chargePointId, String reason) {
        markOffline(chargePointId, reason, true);
    }

    public void markOffline(String chargePointId, String reason, boolean removeConnection) {
        if (chargePointId == null || chargePointId.isBlank() || "unknown".equalsIgnoreCase(chargePointId)) {
            return;
        }

        if (removeConnection) {
            connectionManager.removeConnection(chargePointId);
        }
        try {
            connectionRepository.findByChargePointId(chargePointId).ifPresent(connection -> {
                connection.setDisconnectedAt(Instant.now());
                connection.setActive(false);
                connectionRepository.save(connection);
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to persist OCPP offline state for {}: {}", chargePointId, ex.getMessage());
        }

        stationServiceClient.markChargePointUnavailable(chargePointId, reason);
        log.info("Marked charge point {} offline due to {}", chargePointId, reason);
    }
}
