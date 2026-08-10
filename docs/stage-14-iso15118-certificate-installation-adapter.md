# Stage 14: OCPP 2.0.1 ISO 15118 certificate-installation adapter

## Goal

Add the missing OCPP 2.0.1 `Get15118EVCertificate` protocol adapter between an authenticated charging-station WebSocket and the Plug & Charge platform internal installation API. The release is deployed dark. It does not enable certificate installation, CPS traffic, conformance submission, or simulator credential generation.

## Protocol contract

The handler is registered only when `OCPP_ISO15118_CERTIFICATE_INSTALLATION_ENABLED=true`.

An accepted request must:

- arrive on a connection negotiated as `ocpp2.0.1` (`OCPP201` internally);
- use action `Install`;
- use schema `urn:iso:15118:2:2013:MsgDef`;
- include a non-empty Base64 `exiRequest` of at most 64,000 characters whose decoded size is at most 48,000 bytes, leaving room for the JSON-RPC envelope inside the 65,536-byte WebSocket text limit;
- carry a bounded JSON-RPC message ID and a registered charging-station ID.

The adapter never parses EXI. It sends the opaque Base64 request to:

`POST /api/v1/internal/iso15118/certificate-installations`

An accepted backend result is returned as `{ "status": "Accepted", "exiResponse": "..." }`. Provider, readiness, timeout, HTTP, or response-validation failures return `{ "status": "Failed" }` without a diagnostic body that could enumerate certificates, tenants, or provider state.

Malformed protocol fields map to bounded OCPP CallError codes. When the feature is disabled the handler is absent, preserving the existing `NotImplemented` behavior.

## Identity and tenant boundary

- Station identity comes only from the authenticated WebSocket path/handshake and is revalidated against station-service registration.
- The current deployment is one tenant per OCPP workload. `OCPP_PNC_TENANT_ID` supplies that deployment tenant; it is never accepted from the charger payload.
- The PnC request carries a short-lived HMAC-signed trusted identity with only `ROLE_PNC_PROTOCOL_ADAPTER`.
- The signing secret is read from `APP_INTERNAL_ACCESS_CONTEXT_SECRET`; it is never logged or returned.
- The internal API receives the station ID and original OCPP message ID, allowing the PnC platform to enforce tenant-scoped idempotency.

Moving to shared multi-tenant OCPP workloads requires station-service to return authoritative tenant ownership. Until that schema exists, a pod must serve exactly one configured tenant.

## Data minimization

- EXI, certificates, PCID, eMAID, signed identity payloads, signatures, and secret values are never logged, persisted, traced, or emitted as device events by this adapter.
- Logs contain only station ID, OCPP message ID, bounded outcome category, and byte counts.
- The backend response is checked for shape, Base64 validity, and size before it reaches the socket.
- Request/response objects intentionally omit provider correlation and backend failure details from the OCPP response.

## Failure and availability behavior

- PnC HTTP timeout is bounded below the OCPP command timeout.
- A missing/invalid signing secret prevents the feature-enabled application from starting.
- Station lookup, PnC unavailability, non-2xx responses, malformed backend responses, and readiness rejection fail closed.
- The PnC platform remains the idempotency owner; the OCPP adapter performs no independent retry.
- Existing OCPP 1.6J and non-PnC handlers are unchanged.

## Release gates

1. Unit tests prove request validation, OCPP-version enforcement, signed tenant identity, response redaction, backend failure mapping, and disabled registration.
2. The image is promoted by digest with `OCPP_ISO15118_CERTIFICATE_INSTALLATION_ENABLED=false`.
3. Production validation confirms existing OCPP traffic and connections remain healthy and that the new handler is not registered.
4. Enabling remains prohibited until Stage 13 reports `READY_FOR_EXPLICIT_ACTIVATION`, approved CPS credentials exist, and simulator/physical-charger conformance tests pass.
