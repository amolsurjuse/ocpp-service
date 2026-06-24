package com.electrahub.ocpp.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class HeartbeatMonitorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatMonitorService.class);


    private final OcppConnectionRepository connectionRepository;
    private final ChargePointAvailabilityService availabilityService;

    @Value("${ocpp.heartbeat.timeout-seconds:120}")
    private int heartbeatTimeoutSeconds;

    public HeartbeatMonitorService(
            OcppConnectionRepository connectionRepository,
            ChargePointAvailabilityService availabilityService) {
        this.connectionRepository = connectionRepository;
        this.availabilityService = availabilityService;
    }

    /**
     * Validates check heartbeats for `HeartbeatMonitorService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.service`.
     */
    @Scheduled(fixedDelayString = "${ocpp.heartbeat.check-interval-seconds}000")
    public void checkHeartbeats() {
        LOGGER.info(" Entering HeartbeatMonitorService#checkHeartbeats");
        LOGGER.debug(" Entering HeartbeatMonitorService#checkHeartbeats with debug context");
        log.debug("Checking heartbeats for all connections");

        Instant timeoutThreshold = Instant.now().minus(heartbeatTimeoutSeconds, ChronoUnit.SECONDS);
        connectionRepository.findAllByActiveTrue().forEach(connection -> {
            Instant lastSeen = connection.getLastSeenAt() == null
                    ? connection.getLastHeartbeatAt()
                    : connection.getLastSeenAt();

            if (lastSeen != null && lastSeen.isBefore(timeoutThreshold)) {
                log.warn("OCPP activity timeout for charge point: {}", connection.getChargePointId());
                availabilityService.markOffline(connection, "OCPP_HEARTBEAT_TIMEOUT");
            }
        });
    }

}
