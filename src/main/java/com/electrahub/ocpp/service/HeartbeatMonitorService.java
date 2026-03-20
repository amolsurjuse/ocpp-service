package com.electrahub.ocpp.service;

import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.websocket.ConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class HeartbeatMonitorService {

    private final ConnectionManager connectionManager;
    private final OcppConnectionRepository connectionRepository;

    @Value("${ocpp.heartbeat.timeout-seconds:120}")
    private int heartbeatTimeoutSeconds;

    public HeartbeatMonitorService(
            ConnectionManager connectionManager,
            OcppConnectionRepository connectionRepository) {
        this.connectionManager = connectionManager;
        this.connectionRepository = connectionRepository;
    }

    @Scheduled(fixedDelayString = "${ocpp.heartbeat.check-interval-seconds}000")
    public void checkHeartbeats() {
        log.debug("Checking heartbeats for all connections");

        connectionRepository.findAllByActiveTrue().forEach(connection -> {
            if (connection.getLastHeartbeatAt() != null) {
                Instant timeoutThreshold = Instant.now().minus(heartbeatTimeoutSeconds, ChronoUnit.SECONDS);

                if (connection.getLastHeartbeatAt().isBefore(timeoutThreshold)) {
                    log.warn("Heartbeat timeout for charge point: {}", connection.getChargePointId());
                    markConnectionAsOffline(connection);
                }
            }
        });
    }

    private void markConnectionAsOffline(OcppConnection connection) {
        connection.setActive(false);
        connection.setDisconnectedAt(Instant.now());
        connectionRepository.save(connection);
        connectionManager.removeConnection(connection.getChargePointId());
        log.info("Marked connection as offline: {}", connection.getChargePointId());
    }

}
