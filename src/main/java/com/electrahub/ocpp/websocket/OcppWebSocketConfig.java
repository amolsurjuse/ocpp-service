package com.electrahub.ocpp.websocket;

import com.electrahub.ocpp.service.OcppMessageRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class OcppWebSocketConfig implements WebSocketConfigurer {

    @Value("${ocpp.websocket.max-text-message-size:65536}")
    private int maxTextMessageSize;

    @Value("${ocpp.websocket.max-binary-message-size:65536}")
    private int maxBinaryMessageSize;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ocppWebSocketHandler(), "/ws/ocpp/{chargePointId}")
                .setAllowedOrigins("*");
    }

    @Bean
    public WebSocketHandler ocppWebSocketHandler(
            ConnectionManager connectionManager,
            OcppMessageRouter messageRouter,
            com.electrahub.ocpp.repository.OcppConnectionRepository connectionRepository,
            com.electrahub.ocpp.service.OcppMessageLogService messageLogService) {
        return new OcppWebSocketHandler(connectionManager, messageRouter, connectionRepository, messageLogService);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageSize);
        return container;
    }

}
