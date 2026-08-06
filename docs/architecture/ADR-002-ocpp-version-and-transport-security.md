# ADR-002: OCPP Version and Transport Security Posture

Status: Accepted for staged implementation

Date: 2026-08-06

## Context

The CSMS currently accepts WebSocket connections at `/ws/ocpp/{chargePointId}` with any origin. It infers the protocol from the first `BootNotification` payload and does not authenticate the charging station during the HTTP upgrade. The simulator fleet connects over the private Kubernetes network with `ws://` and does not send `Sec-WebSocket-Protocol` or HTTP Basic credentials.

The implemented application paths support OCPP 1.6J and a subset of OCPP 2.0.1. OCPP 2.1 is not implemented. OCPP 1.6 application logic is not compatible with OCPP 2.x; protocol selection must therefore be explicit at the handshake instead of inferred after messages arrive.

The Open Charge Alliance defines three transport security profiles: Basic authentication without TLS, TLS plus Basic authentication, and TLS with client certificates. It recommends the TLS profiles. The current deployment does not qualify for Security Profile 2 or 3. The target baseline for managed charging stations is Security Profile 2; Security Profile 3 remains an opt-in certificate lifecycle stage.

References:

- https://openchargealliance.org/faq/
- https://openchargealliance.org/certificationocpp/certification-ocpp-2-0-1/
- https://openchargealliance.org/wp-content/uploads/2026/01/ocpp_security_operations_guide-2.pdf

## Decision

### Supported protocol matrix

| Protocol | WebSocket subprotocol | Posture |
| --- | --- | --- |
| OCPP 1.6 JSON | `ocpp1.6` | Supported |
| OCPP 2.0.1 | `ocpp2.0.1` | Partial; only implemented actions are advertised |
| OCPP 2.1 | `ocpp2.1` | Rejected until a separate compatibility implementation is complete |

The negotiated subprotocol is stored on the WebSocket session and becomes the authoritative protocol for routing and command selection. Payload-based inference remains only as a compatibility signal in audit mode and must emit a mismatch metric when it disagrees with the handshake.

### Staged admission policy

Handshake admission is controlled by `OCPP_HANDSHAKE_SECURITY_MODE`:

- `DISABLED`: compatibility escape hatch; no security decision is made.
- `AUDIT`: accept existing chargers, negotiate recognized subprotocols when supplied, and record missing protocol, unsupported protocol, missing credentials, malformed credentials, identity mismatch, and insecure transport.
- `ENFORCE`: reject missing/unsupported subprotocols and invalid credentials before allocating a WebSocket session.

Development and production first receive `AUDIT`. The simulator connector is then updated to send `ocpp1.6` or `ocpp2.0.1` and Basic authentication. Enforcement is promoted only after the audit counters remain at zero for the complete registered fleet through a reconnect cycle.

### Identity and credentials

The Basic username must exactly equal the normalized `{chargePointId}` path segment. Passwords must not be logged or persisted in plaintext. Long-term charger credentials will be individually generated, stored as adaptive hashes, rotated through an internal management operation, and cached only for bounded verification. A shared simulator credential may be used only for synthetic fleet workloads and is not a production-hardware credential design.

### Transport boundary

External charging stations must use `wss://` terminated by a dedicated OCPP ingress or load balancer with TLS 1.2 or newer. The backend may use private `ws://` only when the ingress-to-pod network is explicitly trusted and isolated. Direct public exposure of the ClusterIP service is forbidden.

Security Profile 3 requires charging-station client certificates, trust-chain validation, revocation/expiry operations, and certificate rotation. It is intentionally not claimed by this stage.

### Origin policy

Charging stations are not browser applications, so browser CORS is not the trust control. In `ENFORCE`, absent `Origin` is accepted after charger authentication; a supplied origin must match the configured allowlist. Wildcard origin configuration is removed.

### Observability and privacy

Expose counters tagged only by outcome and negotiated protocol. Never tag metrics with charge-point IDs and never log Authorization header values. Audit records may contain the normalized station ID and remote address with the existing retention controls.

Required outcomes include `accepted`, `missing_protocol`, `unsupported_protocol`, `missing_credentials`, `malformed_credentials`, `identity_mismatch`, `invalid_credentials`, `insecure_transport`, and `protocol_mismatch`.

## Rollout and rollback

1. Deploy CSMS handshake audit and protocol negotiation to development.
2. Reconnect the simulated fleet and confirm no connection regression.
3. Update the connector client to send protocol and credentials; repeat reconnect and command tests.
4. Enable enforcement in development and verify OCPP 1.6J and OCPP 2.0.1 fixtures plus rejection tests.
5. Deploy the exact validated binaries to production in audit mode.
6. Enable production enforcement only after every real charger has an assigned credential and the audit window contains no legacy clients.

Rollback changes the mode to `AUDIT` or `DISABLED`; it does not require a binary rollback. TLS termination retains its previously valid certificate during application rollback.

## Acceptance criteria

- Supported protocol versions are explicit in code, documentation, metrics, and the handshake response.
- Unknown or missing protocols and credentials are observable before enforcement.
- In enforcement mode, an unauthenticated client, a path/username mismatch, and `ocpp2.1` are rejected.
- Valid OCPP 1.6J and OCPP 2.0.1 simulator clients reconnect and complete Heartbeat/command exchange.
- Authorization values never appear in logs, metrics, or API responses.
- Development and production retain a tested configuration-only rollback to audit mode.

## Consequences

This stage makes the current posture honest and creates a safe route to Security Profile 2 without disconnecting legacy hardware. It does not claim full OCPP 2.0.1 conformance, OCPP 2.1 support, or Security Profile 3.
