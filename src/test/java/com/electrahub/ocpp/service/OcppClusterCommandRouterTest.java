package com.electrahub.ocpp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.electrahub.ocpp.websocket.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;

class OcppClusterCommandRouterTest {

    @Test
    void routesCommandToOwnerAndCompletesOnOriginResult() throws Exception {
        ConnectionManager connections = mock(ConnectionManager.class);
        when(connections.getNodeId()).thenReturn("node-a");
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.convertAndSend(any(), any())).thenReturn(1L);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCommandService> commands = mock(ObjectProvider.class);
        ObjectMapper mapper = mapper();
        OcppClusterCommandRouter router = new OcppClusterCommandRouter(
                connections, redis, mapper, commands, new SimpleMeterRegistry(), true, 5);

        CompletableFuture<com.fasterxml.jackson.databind.JsonNode> future = router.route(
                "node-b", "EH-001", "Reset", mapper.createObjectNode().put("type", "Soft"));

        ArgumentCaptor<String> commandJson = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("ocpp:node:commands:node-b"), commandJson.capture());
        OcppClusterCommandRouter.RoutedCommand command = mapper.readValue(
                commandJson.getValue(), OcppClusterCommandRouter.RoutedCommand.class);

        OcppClusterCommandRouter.RoutedResult result = new OcppClusterCommandRouter.RoutedResult(
                command.commandId(), true, mapper.createObjectNode().put("status", "Accepted"), null, null);
        router.onMessage(message(router.resultChannel(), mapper.writeValueAsString(result)), null);

        assertThat(future.join().path("status").asText()).isEqualTo("Accepted");
    }

    @Test
    void owningNodeExecutesAgainstItsLocalSocketAndReturnsResult() throws Exception {
        ConnectionManager connections = mock(ConnectionManager.class);
        when(connections.getNodeId()).thenReturn("node-b");
        when(connections.isLocalConnectionOwner("EH-001")).thenReturn(true);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.convertAndSend(any(), any())).thenReturn(1L);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCommandService> commands = mock(ObjectProvider.class);
        RemoteCommandService commandService = mock(RemoteCommandService.class);
        when(commands.getObject()).thenReturn(commandService);
        ObjectMapper mapper = mapper();
        when(commandService.sendCommandLocally(eq("EH-001"), eq("Reset"), any(), eq("ocpp-message-1")))
                .thenReturn(CompletableFuture.completedFuture(
                        mapper.createObjectNode().put("status", "Accepted")));
        OcppClusterCommandRouter router = new OcppClusterCommandRouter(
                connections, redis, mapper, commands, new SimpleMeterRegistry(), true, 5);
        OcppClusterCommandRouter.RoutedCommand command = new OcppClusterCommandRouter.RoutedCommand(
                java.util.UUID.randomUUID(), "node-a", "EH-001", "Reset",
                mapper.createObjectNode().put("type", "Soft"), "ocpp-message-1", java.time.Instant.now());

        router.onMessage(message(router.commandChannel(), mapper.writeValueAsString(command)), null);

        ArgumentCaptor<String> resultJson = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("ocpp:node:results:node-a"), resultJson.capture());
        OcppClusterCommandRouter.RoutedResult result = mapper.readValue(
                resultJson.getValue(), OcppClusterCommandRouter.RoutedResult.class);
        assertThat(result.success()).isTrue();
        assertThat(result.payload().path("status").asText()).isEqualTo("Accepted");
    }

    @Test
    void failsImmediatelyWhenTheRecordedOwnerHasNoCommandSubscriber() {
        ConnectionManager connections = mock(ConnectionManager.class);
        when(connections.getNodeId()).thenReturn("node-a");
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.convertAndSend(any(), any())).thenReturn(0L);
        @SuppressWarnings("unchecked")
        ObjectProvider<RemoteCommandService> commands = mock(ObjectProvider.class);
        OcppClusterCommandRouter router = new OcppClusterCommandRouter(
                connections, redis, mapper(), commands, new SimpleMeterRegistry(), true, 5);

        CompletableFuture<com.fasterxml.jackson.databind.JsonNode> future = router.route(
                "node-gone", "EH-001", "Reset", mapper().createObjectNode());

        assertThat(future).isCompletedExceptionally();
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static Message message(String channel, String body) {
        Message message = mock(Message.class);
        when(message.getChannel()).thenReturn(channel.getBytes(StandardCharsets.UTF_8));
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
