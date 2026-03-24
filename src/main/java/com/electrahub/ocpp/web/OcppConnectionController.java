package com.electrahub.ocpp.web;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.websocket.ConnectionManager;
import com.electrahub.ocpp.web.dto.ConnectionCountDto;
import com.electrahub.ocpp.web.dto.ConnectionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ocpp/connections")
@Slf4j
public class OcppConnectionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppConnectionController.class);


    private final OcppConnectionRepository connectionRepository;
    private final ConnectionManager connectionManager;

    public OcppConnectionController(
            OcppConnectionRepository connectionRepository,
            ConnectionManager connectionManager) {
        this.connectionRepository = connectionRepository;
        this.connectionManager = connectionManager;
    }

    @GetMapping
    public ResponseEntity<List<ConnectionDto>> listConnections(
            /**
             * Executes request param for `OcppConnectionController`.
             *
             * <p>Detailed behavior: follows the current implementation path and
             * enforces component-specific rules in `com.electrahub.ocpp.web`.
             * @param size input consumed by RequestParam.
             * @return result produced by RequestParam.
             */
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
                LOGGER.info("CODEx_ENTRY_LOG: Entering OcppConnectionController#RequestParam");
                LOGGER.debug("CODEx_ENTRY_LOG: Entering OcppConnectionController#RequestParam with debug context");
        Pageable pageable = PageRequest.of(page, size);
        List<OcppConnection> connections = connectionRepository.findAllByActiveTrue();

        List<ConnectionDto> dtos = connections.stream()
            .map(this::toDto)
            .toList();

        log.debug("Retrieved {} active connections", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Retrieves get connection for `OcppConnectionController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.web`.
     * @param chargePointId input consumed by getConnection.
     * @return result produced by getConnection.
     */
    @GetMapping("/{chargePointId}")
    public ResponseEntity<ConnectionDto> getConnection(@PathVariable String chargePointId) {
        return connectionRepository.findByChargePointIdAndActiveTrue(chargePointId)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves get connection count for `OcppConnectionController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.web`.
     * @return result produced by getConnectionCount.
     */
    @GetMapping("/count")
    public ResponseEntity<ConnectionCountDto> getConnectionCount() {
        long activeCount = connectionRepository.countByActiveTrue();
        log.debug("Active connections count: {}", activeCount);
        return ResponseEntity.ok(new ConnectionCountDto(activeCount, 0));
    }

    /**
     * Executes to dto for `OcppConnectionController`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.web`.
     * @param connection input consumed by toDto.
     * @return result produced by toDto.
     */
    private ConnectionDto toDto(OcppConnection connection) {
        return new ConnectionDto(
            connection.getId(),
            connection.getChargePointId(),
            connection.getNodeId(),
            connection.getOcppProtocol(),
            connection.getConnectedAt(),
            connection.getLastHeartbeatAt(),
            connection.getDisconnectedAt(),
            connection.isActive()
        );
    }

}
