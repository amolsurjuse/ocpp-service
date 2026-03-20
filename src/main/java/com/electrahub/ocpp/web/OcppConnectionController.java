package com.electrahub.ocpp.web;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<OcppConnection> connections = connectionRepository.findAllByActiveTrue();

        List<ConnectionDto> dtos = connections.stream()
            .map(this::toDto)
            .toList();

        log.debug("Retrieved {} active connections", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{chargePointId}")
    public ResponseEntity<ConnectionDto> getConnection(@PathVariable String chargePointId) {
        return connectionRepository.findByChargePointIdAndActiveTrue(chargePointId)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    public ResponseEntity<ConnectionCountDto> getConnectionCount() {
        long activeCount = connectionRepository.countByActiveTrue();
        log.debug("Active connections count: {}", activeCount);
        return ResponseEntity.ok(new ConnectionCountDto(activeCount, 0));
    }

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
