package com.electrahub.ocpp.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "ocpp.websocket.enabled", havingValue = "true", matchIfMissing = true)
public class OcppWebSocketConfig implements WebSocketConfigurer {
    private final OcppWebSocketHandler ocppWebSocketHandler;

    public OcppWebSocketConfig(OcppWebSocketHandler ocppWebSocketHandler) {
        this.ocppWebSocketHandler = ocppWebSocketHandler;
    }

    @Value("${ocpp.websocket.max-text-message-size:65536}")
    private int maxTextMessageSize;

    @Value("${ocpp.websocket.max-binary-message-size:65536}")
    private int maxBinaryMessageSize;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ocppWebSocketHandler, "/ws/ocpp/{chargePointId}")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageSize);
        return container;
    }

}
