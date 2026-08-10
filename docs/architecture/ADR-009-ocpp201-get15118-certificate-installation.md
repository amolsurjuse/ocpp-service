# ADR-009: OCPP 2.0.1 `Get15118EVCertificate` boundary

## Status

Accepted for dark implementation. Production activation is not approved.

## Context

An OCPP 2.0.1 charging station sends `Get15118EVCertificate` while the EV and
EVSE are performing ISO 15118-2 certificate installation. The request contains
an opaque, signed EXI document. The CSMS must not decode it as JSON or substitute
the CSR-based OEM/telematics package exposed by the Plug & Charge platform.

The Plug & Charge platform owns certificate-policy and EXI-provider integration.
The OCPP service owns the charger WebSocket, protocol validation, bounded request
handling, and translation to the OCPP response shape.

## Decision

1. Add a dedicated `Get15118EVCertificate` handler behind
   `OCPP_ISO15118_CERTIFICATE_INSTALLATION_ENABLED`, default `false`.
2. Accept the action only for a connection recorded as `OCPP201`.
3. Accept only `action=Install` and schema
   `urn:iso:15118:2:2013:MsgDef` in this slice. `Update` remains fail-closed.
4. Validate that `exiRequest` is canonical Base64 and decodes to 1..262144
   bytes before any downstream call.
5. Forward the charger-supplied OCPP message ID as the idempotency identity.
   The Plug & Charge platform owns durable replay/conflict detection.
6. Return an OCPP CALL_RESULT with `status=Failed` for provider, policy, timeout,
   authentication, or dependency failures. Return `exiResponse` only when the
   Plug & Charge platform returns `Accepted` with a non-empty Base64 value.
7. Never log or persist `exiRequest` or `exiResponse` in the OCPP service.
8. Use the configured tenant only as the current single-tenant compatibility
   bridge. Activation requires an authoritative charge-point-to-tenant mapping;
   a tenant supplied by the charger is never trusted.
9. The internal request uses the existing signed trusted-identity envelope with
   only `PNC_PROTOCOL_ADAPTER`. The actor is a stable service UUID and the
   assertion expires after 30 seconds. This is a compatibility mechanism for
   the current platform; asymmetric workload identity or mTLS is required before
   expanding the shared-secret trust domain.

## Failure and timeout behavior

- Missing protocol marker, non-2.0.1 connection, invalid action/schema/Base64,
  or oversized EXI: fail locally; do not call the Plug & Charge platform.
- Plug & Charge 4xx/5xx, malformed response, timeout, or signature/configuration
  error: return `{"status":"Failed"}`.
- Accepted without a valid `exiResponse`: downgrade to `Failed`.
- The handler is synchronous because an OCPP CALL needs one correlated result;
  the HTTP read timeout must be below the station's OCPP request timeout.

## Rollout gates

1. Unit and router contract tests pass with the feature enabled locally.
2. Production deploys the handler and client with the feature disabled.
3. Existing OCPP 1.6J/2.0.1 actions and connections remain healthy.
4. Before activation, provision an isolated conformance environment, real EXI
   codec/CPS provider, authoritative tenant mapping, and workload identity.
5. Only after those gates may the simulator send a real signed EXI fixture; the
   same WebSocket contract must then work with a physical charger unchanged.

## Consequences

The production binary can recognize the native OCPP action once explicitly
enabled, but it does not claim ISO 15118 conformance while the provider is
unavailable. The simulator remains outside this slice, preventing test-only
payloads from becoming a production protocol.
