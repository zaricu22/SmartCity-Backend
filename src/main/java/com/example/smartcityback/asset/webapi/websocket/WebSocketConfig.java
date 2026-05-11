package com.example.smartcityback.asset.webapi.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration using STOMP over SockJS.
 *
 * Clients connect to /ws and subscribe to topics under /topic.
 * Domain events are pushed to clients via SimpMessagingTemplate in event handlers.
 *
 * Example frontend subscription:
 *   /topic/buildings/{buildingId}/consumption  — real-time consumption updates
 *   /topic/buildings/{buildingId}/devices      — real-time device additions
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // in-memory broker for topics (replace with RabbitMQ/ActiveMQ for production scale)
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback for browsers that don't support native WebSocket
        registry.addEndpoint("/ws").withSockJS();
    }
}
