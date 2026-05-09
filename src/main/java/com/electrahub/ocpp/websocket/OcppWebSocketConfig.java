package com.electrahub.ocpp.websocket;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(OcppWebSocketConfig.class);

    private final OcppWebSocketHandler ocppWebSocketHandler;

    /**
     * Executes ocpp web socket config for `OcppWebSocketConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param ocppWebSocketHandler input consumed by OcppWebSocketConfig.
     */
    public OcppWebSocketConfig(OcppWebSocketHandler ocppWebSocketHandler) {
        LOGGER.info(" Entering OcppWebSocketConfig#OcppWebSocketConfig");
        LOGGER.debug(" Entering OcppWebSocketConfig#OcppWebSocketConfig with debug context");
        this.ocppWebSocketHandler = ocppWebSocketHandler;
    }

    @Value("${ocpp.websocket.max-text-message-size:65536}")
    private int maxTextMessageSize;

    @Value("${ocpp.websocket.max-binary-message-size:65536}")
    private int maxBinaryMessageSize;

    /**
     * Creates register web socket handlers for `OcppWebSocketConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @param registry input consumed by registerWebSocketHandlers.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ocppWebSocketHandler, "/ws/ocpp/{chargePointId}")
                .setAllowedOrigins("*");
    }

    /**
     * Creates create web socket container for `OcppWebSocketConfig`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.ocpp.websocket`.
     * @return result produced by createWebSocketContainer.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageSize);
        return container;
    }

}
