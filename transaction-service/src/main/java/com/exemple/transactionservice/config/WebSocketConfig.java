package com.exemple.transactionservice.config;

import com.exemple.transactionservice.websocket.WebSocketAssistantController;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration WebSocket
 * 
 * Supporte 2 modes:
 * 1. STOMP over WebSocket (/ws/upload-progress) - pour upload tracking
 * 2. Raw WebSocket (/ws/assistant) - pour RAG streaming
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private final WebSocketAssistantController handler;

    public WebSocketConfig(WebSocketAssistantController handler) {
        this.handler = handler;
    }

    // ========================================================================
    // STOMP CONFIGURATION (pour upload progress)
    // ========================================================================
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/upload-progress")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    // ========================================================================
    // RAW WEBSOCKET CONFIGURATION (pour RAG assistant)
    // ========================================================================
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/assistant")
            .setAllowedOrigins("*") // Configure properly in production
            .withSockJS(); // Fallback pour anciens navigateurs
    }
}