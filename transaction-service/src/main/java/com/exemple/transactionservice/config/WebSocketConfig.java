package com.exemple.transactionservice.config;

import com.exemple.transactionservice.websocket.WebSocketAssistantController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * Configuration WebSocket Complète
 * 
 * Support 2 modes:
 * 1. STOMP over WebSocket (/ws) - pour upload progress tracking
 * 2. Raw WebSocket (/ws/assistant) - pour RAG streaming
 * 
 * Supporte:
 * - WebSocket natif (recommandé)
 * - SockJS fallback (navigateurs anciens)
 * - CORS configuré
 */
@Slf4j
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private final WebSocketAssistantController handler;

    public WebSocketConfig(WebSocketAssistantController handler) {
        this.handler = handler;
        log.info("🔌 WebSocket Configuration initialized");
    }

    // ========================================================================
    // STOMP CONFIGURATION (Upload Progress Tracking)
    // ========================================================================
    
    /**
     * Configure message broker
     * 
     * /topic - broadcast messages (1-to-many)
     * /queue - point-to-point messages (1-to-1)
     * /app - application destination prefix
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker
        config.enableSimpleBroker("/topic", "/queue");
        
        // Application destination prefix
        config.setApplicationDestinationPrefixes("/app");
        
        // User destination prefix (pour messages privés)
        config.setUserDestinationPrefix("/user");
        
        log.info("✅ Message broker configured: /topic, /queue, /app");
    }

    /**
     * Register STOMP endpoints
     * 
     * Endpoint principal: /ws
     * Topics disponibles:
     * - /topic/upload-progress/{batchId}
     * - /topic/ingestion-status
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        
        // ========================================================================
        // ENDPOINT 1: WebSocket natif (RECOMMANDÉ)
        // ========================================================================
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");
                // "http://localhost",
                // "http://localhost:8080",       // nginx port 80
                // "http://localhost:4200",      // ✅ Angular dev server
                // "http://34.53.187.62:8080",   // ✅ VM dev
                // "https://*.run.app",         // ✅ Cloud Run prod
                // "https://*"               // ✅ tous les HTTPS
            //);
        
        log.info("✅ STOMP endpoint registered: /ws (native WebSocket)");
        
        // ========================================================================
        // ENDPOINT 2: SockJS fallback (OPTIONNEL)
        // ========================================================================
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
            .setSessionCookieNeeded(false)
            .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
        
        log.info("✅ STOMP endpoint registered: /ws (with SockJS fallback)");
    }

    // ========================================================================
    // RAW WEBSOCKET CONFIGURATION (RAG Assistant Streaming)
    // ========================================================================
    
    /**
     * Register raw WebSocket handlers
     * 
     * Endpoint: /ws/assistant
     * Usage: Streaming RAG responses
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        
        // ========================================================================
        // RAW WEBSOCKET: Native
        // ========================================================================
        registry.addHandler(handler, "/ws/assistant")
            .setAllowedOrigins("*");
            //     "http://localhost",    // ✅ tous les localhost 
            //     "http://localhost:8080", // nginx port 80
            //     "http://localhost:4200", // ✅ Angular dev server
            //     "http://34.53.187.62:8080",   // ✅ VM dev
            //     "https://*.run.app", // ✅ Cloud Run prod
            //     "https://*" // ✅ tous les HTTPS
            // );
        
        log.info("✅ Raw WebSocket handler registered: /ws/assistant (native)");
        
        // ========================================================================
        // RAW WEBSOCKET: SockJS fallback
        // ========================================================================
        registry.addHandler(handler, "/ws/assistant")
            .setAllowedOrigins("*")
            .withSockJS()
            .setSessionCookieNeeded(false);
        
        log.info("✅ Raw WebSocket handler registered: /ws/assistant (with SockJS)");
    }
}