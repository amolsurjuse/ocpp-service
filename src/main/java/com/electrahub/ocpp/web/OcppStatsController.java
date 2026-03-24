package com.electrahub.ocpp.web;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.OcppMessageLog;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.repository.OcppMessageLogRepository;
import com.electrahub.ocpp.web.dto.MessageStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ocpp/stats")
@Slf4j
public class OcppStatsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppStatsController.class);


    private final OcppMessageLogRepository messageLogRepository;
    private final OcppConnectionRepository connectionRepository;

    public OcppStatsController(
            OcppMessageLogRepository messageLogRepository,
            OcppConnectionRepository connectionRepository) {
        this.messageLogRepository = messageLogRepository;
        this.connectionRepository = connectionRepository;
    }

    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> getMessageStatistics(
            /**
             * Executes request param for `OcppStatsController`.
             *
             * <p>Detailed behavior: follows the current implementation path and
             * enforces component-specific rules in `com.electrahub.ocpp.web`.
             * @param chargePointId input consumed by RequestParam.
             * @return result produced by RequestParam.
             */
            @RequestParam(required = false) String chargePointId) {
                LOGGER.info("CODEx_ENTRY_LOG: Entering OcppStatsController#RequestParam");
                LOGGER.debug("CODEx_ENTRY_LOG: Entering OcppStatsController#RequestParam with debug context");
        try {
            Map<String, Object> stats = new HashMap<>();

            if (chargePointId != null && !chargePointId.isEmpty()) {
                long inbound = messageLogRepository.countByChargePointIdAndDirection(chargePointId, "INBOUND");
                long outbound = messageLogRepository.countByChargePointIdAndDirection(chargePointId, "OUTBOUND");

                stats.put("chargePointId", chargePointId);
                stats.put("inboundMessages", inbound);
                stats.put("outboundMessages", outbound);
                stats.put("totalMessages", inbound + outbound);
            } else {
                stats.put("message", "Provide chargePointId parameter");
            }

            log.debug("Message statistics: {}", stats);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving message statistics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/connections/history")
    public ResponseEntity<Page<OcppMessageLog>> getConnectionHistory(
            /**
             * Executes request param for `OcppStatsController`.
             *
             * <p>Detailed behavior: follows the current implementation path and
             * enforces component-specific rules in `com.electrahub.ocpp.web`.
             * @param chargePointId input consumed by RequestParam.
             * @return result produced by RequestParam.
             */
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String chargePointId) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            if (chargePointId != null && !chargePointId.isEmpty()) {
                Page<OcppMessageLog> history = messageLogRepository
                    .findByChargePointIdOrderByCreatedAtDesc(chargePointId, pageable);

                log.debug("Retrieved {} messages for {}", history.getTotalElements(), chargePointId);
                return ResponseEntity.ok(history);
            }

            log.debug("No chargePointId provided");
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error retrieving connection history: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

}
