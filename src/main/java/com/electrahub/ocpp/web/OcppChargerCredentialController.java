package com.electrahub.ocpp.web;

import com.electrahub.ocpp.service.OcppChargerCredentialService;
import com.electrahub.ocpp.service.OcppChargerCredentialService.CredentialMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ocpp/internal/charger-credentials")
public class OcppChargerCredentialController {

    private static final String ACTOR_HEADER = "X-ElectraHub-Actor";
    private final OcppChargerCredentialService service;

    public OcppChargerCredentialController(OcppChargerCredentialService service) {
        this.service = service;
    }

    @PutMapping("/{chargePointId}")
    public CredentialMetadata put(
            @PathVariable String chargePointId,
            @Valid @RequestBody PutCredentialRequest request,
            HttpServletRequest httpRequest
    ) {
        return service.put(
                chargePointId,
                request.password(),
                request.validUntil(),
                Duration.ofSeconds(request.rotationOverlapSeconds()),
                httpRequest.getHeader(ACTOR_HEADER)
        );
    }

    @PostMapping("/{chargePointId}/disable")
    public CredentialMetadata disable(@PathVariable String chargePointId, HttpServletRequest request) {
        return service.disable(chargePointId, request.getHeader(ACTOR_HEADER));
    }

    @GetMapping("/{chargePointId}")
    public CredentialMetadata metadata(@PathVariable String chargePointId) {
        return service.metadata(chargePointId);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_CHARGER_CREDENTIAL", exception.getMessage()));
    }

    public record PutCredentialRequest(
            @NotBlank @Size(min = 16, max = 72) String password,
            Instant validUntil,
            @Min(0) @Max(86400) long rotationOverlapSeconds
    ) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
