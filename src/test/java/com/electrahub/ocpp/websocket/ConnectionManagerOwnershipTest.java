package com.electrahub.ocpp.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionManagerOwnershipTest {

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private ConnectionManager connectionManager;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(session.isOpen()).thenReturn(true);
        connectionManager = new ConnectionManager(redisTemplate, 120);
        connectionManager.registerConnection("CP-1", session);
    }

    @Test
    void localSocketIsOwnerOnlyWhenRedisAtomicallyConfirmsItsNodeMarker() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        assertThat(connectionManager.isLocalConnectionOwner("CP-1")).isTrue();
    }

    @Test
    void differentRedisOwnerFailsClosed() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);
        when(valueOperations.get("ocpp:connection:CP-1")).thenReturn("other-node");

        assertThat(connectionManager.isLocalConnectionOwner("CP-1")).isFalse();
        assertThat(connectionManager.connectionOwnership("CP-1"))
                .isEqualTo(ConnectionManager.ConnectionOwnership.REMOTE_OWNER);
    }

    @Test
    void redisFailureFailsClosedWithoutClaimingOwnership() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

        assertThat(connectionManager.isLocalConnectionOwner("CP-1")).isFalse();
    }

    @Test
    void missingLocalSocketDoesNotConsultRedis() {
        assertThat(connectionManager.connectionOwnership("CP-2"))
                .isEqualTo(ConnectionManager.ConnectionOwnership.OFFLINE);

        verify(redisTemplate, never()).execute(
                any(RedisScript.class), anyList(), any(), any());
    }
}
