package com.electrahub.ocpp.service;

import com.electrahub.ocpp.websocket.ConnectionManager;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class OcppDrainService {

    private final ConnectionManager connections;
    private final ApplicationContext applicationContext;

    public OcppDrainService(ConnectionManager connections, ApplicationContext applicationContext) {
        this.connections = connections;
        this.applicationContext = applicationContext;
    }

    public long beginDrain() {
        long activeConnections = connections.beginDrain();
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        return activeConnections;
    }

    @PreDestroy
    public void closeConnectionsForRestart() {
        connections.closeAllForRestart();
    }
}
