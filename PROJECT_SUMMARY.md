# OCPP Service - ElectraHub CSMS

Complete Spring Boot 4.0.1 microservice for handling OCPP (Open Charge Point Protocol) WebSocket connections.

## Project Overview

- **Framework**: Spring Boot 4.0.1
- **Java Version**: 21
- **Port**: 8082
- **Package**: `com.electrahub.ocpp`
- **Build**: Maven 3.9.9
- **Database**: PostgreSQL
- **Cache**: Redis
- **WebSocket Support**: Spring WebSocket

## Project Structure

```
ocpp-service/
├── pom.xml                          # Maven build configuration
├── Dockerfile                       # Multi-stage Docker build
├── PROJECT_SUMMARY.md              # This file
└── src/
    ├── main/
    │   ├── java/com/electrahub/ocpp/
    │   │   ├── OcppServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── RedisConfig.java
    │   │   │   ├── AsyncConfig.java
    │   │   │   └── JacksonConfig.java
    │   │   ├── domain/
    │   │   │   ├── OcppConnection.java
    │   │   │   ├── OcppMessageLog.java
    │   │   │   ├── OcppPendingRequest.java
    │   │   │   └── enums/
    │   │   │       ├── OcppMessageType.java
    │   │   │       ├── OcppAction.java
    │   │   │       └── OcppErrorCode.java
    │   │   ├── repository/
    │   │   │   ├── OcppConnectionRepository.java
    │   │   │   ├── OcppMessageLogRepository.java
    │   │   │   └── OcppPendingRequestRepository.java
    │   │   ├── websocket/
    │   │   │   ├── OcppWebSocketConfig.java
    │   │   │   ├── OcppWebSocketHandler.java
    │   │   │   ├── OcppJsonRpcMessage.java
    │   │   │   └── ConnectionManager.java
    │   │   ├── service/
    │   │   │   ├── OcppMessageHandler.java
    │   │   │   ├── OcppMessageRouter.java
    │   │   │   ├── OcppMessageLogService.java
    │   │   │   ├── RemoteCommandService.java
    │   │   │   └── HeartbeatMonitorService.java
    │   │   ├── handler/ (OCPP 1.6J/2.0.1 message handlers)
    │   │   │   ├── BootNotificationHandler.java
    │   │   │   ├── HeartbeatHandler.java
    │   │   │   ├── AuthorizeHandler.java
    │   │   │   ├── StartTransactionHandler.java
    │   │   │   ├── StopTransactionHandler.java
    │   │   │   ├── MeterValuesHandler.java
    │   │   │   ├── StatusNotificationHandler.java
    │   │   │   ├── DataTransferHandler.java
    │   │   │   └── TransactionEventHandler.java
    │   │   ├── integration/
    │   │   │   ├── StationServiceClient.java
    │   │   │   └── SessionServiceClient.java
    │   │   ├── exception/
    │   │   │   ├── ChargePointNotConnectedException.java
    │   │   │   ├── OcppCommandTimeoutException.java
    │   │   │   ├── OcppProtocolException.java
    │   │   │   ├── ApiError.java
    │   │   │   └── RestExceptionHandler.java
    │   │   ├── web/
    │   │   │   ├── OcppConnectionController.java
    │   │   │   ├── RemoteCommandController.java
    │   │   │   ├── OcppStatsController.java
    │   │   │   └── dto/
    │   │   │       ├── RemoteStartRequest.java
    │   │   │       ├── RemoteStopRequest.java
    │   │   │       ├── ResetRequest.java
    │   │   │       ├── UnlockConnectorRequest.java
    │   │   │       ├── SetChargingProfileRequest.java
    │   │   │       ├── ChangeConfigurationRequest.java
    │   │   │       ├── GetConfigurationRequest.java
    │   │   │       ├── TriggerMessageRequest.java
    │   │   │       ├── CommandResponse.java
    │   │   │       ├── ConnectionDto.java
    │   │   │       ├── ConnectionCountDto.java
    │   │   │       └── MessageStatsDto.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-test.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/
    │               ├── 001-create-ocpp-connections.yaml
    │               ├── 002-create-ocpp-message-log.yaml
    │               └── 003-create-ocpp-pending-requests.yaml
    └── test/
        ├── java/com/electrahub/ocpp/
        │   └── OcppServiceApplicationTests.java
        └── resources/
            └── application-test.yml
```

## Key Features

### 1. WebSocket Management
- OCPP 1.6J and 2.0.1 protocol support
- JSON-RPC 2.0 message serialization
- Multi-node connection awareness via Redis
- Connection lifecycle tracking

### 2. Message Routing
- 8+ OCPP message handlers (BootNotification, Heartbeat, Authorize, StartTransaction, StopTransaction, MeterValues, StatusNotification, DataTransfer, TransactionEvent)
- Request/response correlation with timeout handling
- Async message logging

### 3. Remote Commands
- RemoteStartTransaction
- RemoteStopTransaction
- Reset (Hard/Soft)
- UnlockConnector
- SetChargingProfile
- ChangeConfiguration
- GetConfiguration
- TriggerMessage

### 4. Monitoring
- Heartbeat timeout monitoring (configurable, default 120s)
- Message statistics and history
- Active connection tracking
- Full request/response logging

### 5. Integration
- Station Service Client (station-service:8081)
- Session Service Client (session-service:8083)
- RestClient-based HTTP integration
- Graceful error handling

### 6. Data Persistence
- PostgreSQL database with Liquibase migrations
- 3 main tables: ocpp_connections, ocpp_message_log, ocpp_pending_requests
- Indexed queries for performance

### 7. Security
- STATELESS session management
- CSRF disabled for WebSocket support
- Method-level security with @EnableMethodSecurity
- Public API endpoints for /api/v1/**, /ws/**, /actuator/**, /swagger-ui/**

## Configuration

### Environment Variables
- `DB_USERNAME` - PostgreSQL username (default: postgres)
- `DB_PASSWORD` - PostgreSQL password (default: postgres)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `STATION_SERVICE_URL` - Station service URL (default: http://localhost:8081)
- `SESSION_SERVICE_URL` - Session service URL (default: http://localhost:8083)
- `NODE_ID` - Node identifier for multi-node deployments

### Application Properties
- `ocpp.heartbeat.timeout-seconds` - 120 (seconds before connection marked offline)
- `ocpp.heartbeat.check-interval-seconds` - 30 (check frequency)
- `ocpp.message.response-timeout-seconds` - 30 (command timeout)
- `ocpp.websocket.max-text-message-size` - 65536 (bytes)
- `ocpp.websocket.max-binary-message-size` - 65536 (bytes)

## Database Setup

### Required PostgreSQL Database
```bash
createdb ocpp_db
```

Liquibase will automatically create tables on startup.

### Tables
1. **ocpp_connections** - Active charge point connections
2. **ocpp_message_log** - Message history
3. **ocpp_pending_requests** - Waiting for responses

## REST API Endpoints

### Connection Management
- `GET /api/v1/ocpp/connections` - List active connections
- `GET /api/v1/ocpp/connections/{chargePointId}` - Get connection details
- `GET /api/v1/ocpp/connections/count` - Get active connection count

### Remote Commands
- `POST /api/v1/ocpp/commands/{chargePointId}/remote-start` - Start charging
- `POST /api/v1/ocpp/commands/{chargePointId}/remote-stop` - Stop charging
- `POST /api/v1/ocpp/commands/{chargePointId}/reset` - Reset charge point
- `POST /api/v1/ocpp/commands/{chargePointId}/unlock-connector` - Unlock connector
- `POST /api/v1/ocpp/commands/{chargePointId}/set-charging-profile` - Set profile
- `POST /api/v1/ocpp/commands/{chargePointId}/change-configuration` - Change config
- `POST /api/v1/ocpp/commands/{chargePointId}/get-configuration` - Get config
- `POST /api/v1/ocpp/commands/{chargePointId}/trigger-message` - Trigger message

### Statistics
- `GET /api/v1/ocpp/stats/messages?chargePointId=...` - Message statistics
- `GET /api/v1/ocpp/stats/connections/history` - Connection history (pageable)

## WebSocket Endpoints

- `WS /ws/ocpp/{chargePointId}` - OCPP WebSocket connection

## Docker Build

```bash
docker build -t electrahub/ocpp-service:latest .
docker run -p 8082:8082 \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=password \
  -e REDIS_HOST=redis \
  -e STATION_SERVICE_URL=http://station-service:8081 \
  -e SESSION_SERVICE_URL=http://session-service:8083 \
  electrahub/ocpp-service:latest
```

## Dependencies (pom.xml)

- Spring Boot Web Starter
- Spring Boot WebSocket Starter
- Spring Boot Data JPA
- Spring Boot Data Redis
- Spring Boot Security
- Spring Boot Actuator
- Spring Boot Validation
- PostgreSQL JDBC Driver
- Liquibase Core
- SpringDoc OpenAPI 3.0.1 (Swagger UI)
- JJWT 0.12.6 (JWT support)
- Lombok (for annotations)
- H2 Database (test only)

## Testing

### Run Tests
```bash
mvn test
```

### Basic Context Load Test
`src/test/java/com/electrahub/ocpp/OcppServiceApplicationTests.java`

## Deployment

### Prerequisites
- Java 21 JRE
- PostgreSQL 12+
- Redis 6+
- Network access to station-service and session-service

### Health Check
`GET /actuator/health`

### Metrics
`GET /actuator/metrics`

### OpenAPI Documentation
`GET /swagger-ui.html`

## Implementation Notes

1. **OCPP JSON-RPC**: Messages follow OCPP 2.0 JSON array format with message type indicator
2. **Async Processing**: Message logging is async to prevent blocking
3. **Timeout Handling**: Remote commands timeout after 30 seconds
4. **Heartbeat Monitoring**: Scheduled task checks every 30 seconds, marks offline after 120 seconds
5. **Multi-Node Support**: Redis stores connection node info for load balancing
6. **Jakarta EE 10**: All imports use jakarta.* (not javax.*)
7. **Records for DTOs**: Immutable data transfer objects using Java records
8. **UUID Identifiers**: All entities use UUID primary keys

## File Locations

All files created at `/sessions/dazzling-bold-fermi/mnt/projects/ocpp-service/`

Total: 60+ Java classes, 4 configuration files, 7 resource files
