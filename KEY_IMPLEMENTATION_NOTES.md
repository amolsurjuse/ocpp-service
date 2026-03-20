# OCPP Service - Key Implementation Notes

## Core Architecture

### WebSocket Connection Lifecycle

```
1. Client connects to ws://server:8082/ws/ocpp/{chargePointId}
2. OcppWebSocketHandler.afterConnectionEstablished()
   - Extract chargePointId from URI
   - Register in ConnectionManager (in-memory + Redis)
   - Save OcppConnection entity to database
3. Client sends JSON-RPC message
4. OcppWebSocketHandler.handleTextMessage()
   - Parse OcppJsonRpcMessage
   - Route via OcppMessageRouter
   - Execute appropriate handler (BootNotification, Heartbeat, etc.)
   - Send response back
5. Client disconnects
   - OcppWebSocketHandler.afterConnectionClosed()
   - Mark OcppConnection as inactive
   - Remove from ConnectionManager
```

### JSON-RPC Message Format

OCPP uses JSON-RPC 2.0 array format:

```json
// CALL (Request, type=2)
[2, "uuid", "BootNotification", {"chargePointVendor": "...", ...}]

// CALL_RESULT (Response, type=3)
[3, "uuid", {"status": "Accepted", ...}]

// CALL_ERROR (Error, type=4)
[4, "uuid", "INTERNAL_ERROR", "Error description", {...}]
```

### Message Handler Pattern

All OCPP message handlers implement OcppMessageHandler interface:

```java
@Component
public class BootNotificationHandler implements OcppMessageHandler {
    @Override
    public String getAction() { return "BootNotification"; }
    
    @Override
    public JsonNode handle(String chargePointId, JsonNode payload) {
        // Process message payload
        // Call integration services (station-service, session-service)
        // Return response as JsonNode
    }
}
```

## Critical Services

### RemoteCommandService

Handles CSMS-to-Charger commands with timeout handling:

```
1. sendCommand(chargePointId, action, payload)
   - Check if charge point is connected
   - Create CompletableFuture for response
   - Send CALL message via WebSocket
   - Store future in pendingResponses map
   - Set 30-second timeout
2. When charger responds with CALL_RESULT
   - OcppMessageRouter extracts messageId
   - Calls resolvePendingResponse(messageId, payload)
   - Completes the CompletableFuture
```

### HeartbeatMonitorService

Scheduled task runs every 30 seconds (configurable):

```
1. Iterate all active OcppConnections from database
2. Check if lastHeartbeatAt is older than 120 seconds (configurable)
3. If timeout detected:
   - Mark connection as inactive
   - Set disconnectedAt timestamp
   - Remove from ConnectionManager
4. This prevents "zombie" connections from hanging around
```

## Multi-Node Deployment

When deployed as multiple instances:

1. **ConnectionManager** stores local connections in ConcurrentHashMap
2. **Redis** stores node assignment: `ocpp:connection:{chargePointId} -> {nodeId}`
3. For remote commands:
   - Check if charge point is local (in local sessions map)
   - If not local, service returns 404 (client should route to correct node)
4. All nodes can read/write to same PostgreSQL database
5. Heartbeat monitor works independently on each node (no conflicts)

## Database Indexes

Carefully designed for performance:

```sql
-- ocpp_connections
CREATE INDEX idx_charge_point_id ON ocpp_connections(charge_point_id);
CREATE INDEX idx_active ON ocpp_connections(active);
-- Used for: findByChargePointIdAndActiveTrue(), findAllByActiveTrue()

-- ocpp_message_log
CREATE INDEX idx_charge_point_id_msg ON ocpp_message_log(charge_point_id);
CREATE INDEX idx_message_id_log ON ocpp_message_log(message_id);
CREATE INDEX idx_created_at ON ocpp_message_log(created_at);
-- Used for: pagination by chargePointId, message lookup, time-based queries

-- ocpp_pending_requests
CREATE INDEX idx_message_id_pending ON ocpp_pending_requests(message_id);
CREATE INDEX idx_charge_point_responded ON ocpp_pending_requests(charge_point_id, responded);
-- Used for: message lookup, finding pending requests
```

## Security Considerations

### Disabled Features
- CSRF protection (incompatible with WebSocket)
- Session creation (STATELESS)
- Authentication (all endpoints open by default)

### To Add Authentication
1. Implement JWT token validation in SecurityConfig
2. Add @PreAuthorize annotations to controllers
3. Create AuthFilter for WebSocket connections
4. Store JWT tokens in HTTP headers or as query parameters

### Current Configuration
```java
.requestMatchers("/api/v1/**").permitAll()    // All API endpoints open
.requestMatchers("/ws/**").permitAll()          // All WebSocket open
.requestMatchers("/actuator/**").permitAll()    // Health checks open
.requestMatchers("/swagger-ui/**").permitAll()  // API docs open
.requestMatchers("/v3/api-docs/**").permitAll() // OpenAPI spec open
```

## Performance Optimizations

### 1. Async Logging
- Message logging doesn't block request handling
- Uses @Async with ThreadPoolTaskExecutor (5-10 threads)
- Improves throughput for high message volumes

### 2. Connection Pooling
- HikariCP (built into Spring Boot)
- PostgreSQL connection pool
- Redis connection pool

### 3. Database Indexes
- All frequently queried columns indexed
- Composite index for charge_point_id + responded
- Time-based index for message cleanup queries

### 4. WebSocket Buffer Sizing
- Configurable message sizes (default 65KB text, 65KB binary)
- Prevents OOM errors from oversized messages
- `ocpp.websocket.max-text-message-size: 65536`

### 5. Heartbeat Monitoring
- Scheduled at 30-second intervals (configurable)
- Only updates connections with timeout
- Prevents query storms

## Error Handling Strategy

### WebSocket Errors
```
If message parsing fails:
  -> Send CALL_ERROR with PROTOCOL_ERROR
If handler throws exception:
  -> Send CALL_ERROR with INTERNAL_ERROR
If connection dies:
  -> Mark as offline, clean up
```

### Remote Command Errors
```
If charge point not connected:
  -> Throw ChargePointNotConnectedException (404)
If command times out (30s):
  -> Throw OcppCommandTimeoutException (504)
If station/session service unavailable:
  -> Return error response
```

### API Errors
```
RestExceptionHandler catches all exceptions:
  -> Format as ApiError record
  -> Include timestamp, status, error code, message, path
  -> Return appropriate HTTP status
```

## Configuration Profiles

### Default (application.yml)
- PostgreSQL at localhost:5432
- Redis at localhost:6379
- Station service at http://localhost:8081
- Session service at http://localhost:8083
- Heartbeat timeout: 120 seconds

### Test (application-test.yml)
- H2 in-memory database
- Liquibase disabled (DDL auto)
- Local Redis (can be mocked)
- No integration service calls in tests

## Testing Strategy

### Unit Tests
- Test individual handlers with mocked services
- Test message parsing/serialization
- Test exception handling

### Integration Tests
- Full Spring context with H2 database
- Mock WebSocket sessions
- Test end-to-end message flow

### Load Tests
- Simulate multiple concurrent connections
- Test heartbeat timeout under load
- Measure async logging throughput

## Deployment Checklist

Before deploying to production:

1. Create PostgreSQL database: `createdb ocpp_db`
2. Set environment variables for DB credentials
3. Set REDIS_HOST and REDIS_PORT
4. Set STATION_SERVICE_URL and SESSION_SERVICE_URL
5. Configure NODE_ID for load balancer awareness
6. Configure heartbeat timeout based on your charge points
7. Increase JVM heap if expecting 1000+ connections
8. Set up monitoring for:
   - Connection count
   - Message latency
   - Error rates
   - Database connection pool usage
   - Redis memory usage

## Integration with Other Services

### Station Service (Port 8081)
```
POST /api/v1/stations/{chargePointId}/connectors/{connectorId}/status
Body: {"status": "Available"|"Occupied"|"Unavailable"|"Faulted"}

GET /api/v1/stations/{chargePointId}
Returns: Station details
```

### Session Service (Port 8083)
```
POST /api/v1/sessions
Body: {chargePointId, connectorId, idTag, meterStart, startTime}
Returns: {sessionId, ...}

POST /api/v1/sessions/{transactionId}/stop
Body: {meterId, endTime, reason}

POST /api/v1/sessions/{transactionId}/meter-values
Body: {timestamp, meterValue[], ...}

POST /api/v1/sessions/authorize
Body: {idTag}
Returns: {authorized: true|false}
```

## Key Code Locations

- **WebSocket Configuration**: `/websocket/OcppWebSocketConfig.java`
- **Message Routing Logic**: `/service/OcppMessageRouter.java`
- **Connection Management**: `/websocket/ConnectionManager.java`
- **Remote Commands**: `/service/RemoteCommandService.java`
- **Security Config**: `/config/SecurityConfig.java`
- **Database Setup**: `/resources/db/changelog/`

## Future Enhancements

1. Add JWT authentication to SecurityConfig
2. Implement WebSocket authentication per RFC 6455
3. Add transaction event batching for better performance
4. Implement message compression for bandwidth
5. Add circuit breakers for integration service calls
6. Implement OCPP 2.0.1 compliance tests
7. Add metrics collection for charging analytics
8. Implement WebSocket load balancer sticky sessions

