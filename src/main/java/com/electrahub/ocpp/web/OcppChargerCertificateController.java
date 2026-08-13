package com.electrahub.ocpp.web;

import com.electrahub.ocpp.service.ForwardedClientCertificateParser;
import com.electrahub.ocpp.service.OcppChargerCertificateService;
import com.electrahub.ocpp.service.OcppChargerCertificateService.CertificateMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ocpp/internal/charger-certificates")
public class OcppChargerCertificateController {
    private static final String ACTOR_HEADER = "X-ElectraHub-Actor";

    private final OcppChargerCertificateService service;
    private final ForwardedClientCertificateParser parser;

    public OcppChargerCertificateController(
            OcppChargerCertificateService service,
            ForwardedClientCertificateParser parser
    ) {
        this.service = service;
        this.parser = parser;
    }

    @PutMapping("/{chargePointId}")
    public CertificateMetadata register(
            @PathVariable String chargePointId,
            @Valid @RequestBody PutCertificateRequest request,
            HttpServletRequest httpRequest
    ) {
        return service.register(chargePointId, parser.parse(request.certificatePem()),
                Duration.ofSeconds(request.rotationOverlapSeconds()), httpRequest.getHeader(ACTOR_HEADER));
    }

    @PostMapping("/{chargePointId}/{fingerprint}/revoke")
    public CertificateMetadata revoke(
            @PathVariable String chargePointId,
            @PathVariable String fingerprint,
            HttpServletRequest request
    ) {
        return service.revoke(chargePointId, fingerprint, request.getHeader(ACTOR_HEADER));
    }

    @GetMapping("/{chargePointId}")
    public List<CertificateMetadata> metadata(@PathVariable String chargePointId) {
        return service.metadata(chargePointId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("INVALID_CHARGER_CERTIFICATE", exception.getMessage()));
    }

    public record PutCertificateRequest(
            @NotBlank @Size(max = 16384) String certificatePem,
            @Min(0) @Max(86400) long rotationOverlapSeconds
    ) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
