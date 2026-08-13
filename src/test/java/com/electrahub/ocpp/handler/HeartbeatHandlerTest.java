package com.electrahub.ocpp.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.electrahub.ocpp.domain.OcppConnection;
import com.electrahub.ocpp.repository.OcppConnectionRepository;
import com.electrahub.ocpp.websocket.ConnectionManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HeartbeatHandlerTest {

    @Test
    void coalescesDurableHeartbeatWritesWithinOneMinute() {
        ConnectionManager connections = mock(ConnectionManager.class);
        OcppConnectionRepository repository = mock(OcppConnectionRepository.class);
        OcppConnection connection = OcppConnection.builder().chargePointId("EH-TEST-1").build();
        when(repository.findByChargePointId("EH-TEST-1")).thenReturn(Optional.of(connection));
        when(connections.getNodeId()).thenReturn("node-a");
        when(connections.getProtocol("EH-TEST-1")).thenReturn("OCPP16J");

        HeartbeatHandler handler = new HeartbeatHandler(connections, repository);
        handler.handle("EH-TEST-1", null);
        handler.handle("EH-TEST-1", null);

        verify(repository, times(1)).save(connection);
    }
}
