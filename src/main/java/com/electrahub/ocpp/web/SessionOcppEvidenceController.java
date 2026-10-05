package com.electrahub.ocpp.web;

import com.electrahub.ocpp.service.SessionOcppEvidenceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

/** Protected by InternalServiceTokenFilter; caller must first authorize the selected session's data scope. */
@RestController
@RequestMapping("/api/v1/ocpp/internal/session-evidence")
public class SessionOcppEvidenceController {
    private final SessionOcppEvidenceService evidence;
    public SessionOcppEvidenceController(SessionOcppEvidenceService evidence) { this.evidence = evidence; }

    @GetMapping
    public SessionOcppEvidenceService.Evidence collect(@RequestParam String chargePointId,
            @RequestParam(required = false) String transactionId, @RequestParam(required = false) Integer connectorId,
            @RequestParam Instant from, @RequestParam Instant to) {
        try { return evidence.collect(chargePointId, transactionId, connectorId, from, to); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid evidence scope"); }
    }
}
