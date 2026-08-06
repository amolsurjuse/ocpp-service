package com.electrahub.ocpp.web;

import com.electrahub.ocpp.service.OcppDrainService;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ocpp/internal")
public class OcppDrainController {

    private final OcppDrainService drainService;

    public OcppDrainController(OcppDrainService drainService) {
        this.drainService = drainService;
    }

    @PostMapping("/drain")
    public ResponseEntity<DrainResponse> drain() {
        return ResponseEntity.accepted().body(
                new DrainResponse("DRAINING", drainService.beginDrain(), Instant.now()));
    }

    public record DrainResponse(String status, long activeConnections, Instant startedAt) {}
}
