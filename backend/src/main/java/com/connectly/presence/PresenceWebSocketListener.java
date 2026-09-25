package com.connectly.presence;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Keeps presence fresh for users who are purely listening on the WebSocket.
 * The WebSocket principal name is the authenticated user id (see WebSocketConfig).
 */
@Component
public class PresenceWebSocketListener {

    private final PresenceService presence;

    public PresenceWebSocketListener(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long id = userId(event.getUser());
        if (id != null) presence.socketOpened(id);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long id = userId(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        if (id != null) presence.socketClosed(id);
    }

    private static Long userId(Principal principal) {
        if (principal == null || principal.getName() == null) return null;
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
