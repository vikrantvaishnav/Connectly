package com.connectly.chat;

import com.connectly.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Push channel only: the REST API stays the authority for every chat mutation.
 * Clients authenticate with their access token at CONNECT time; subscriptions
 * are restricted to the subscriber's own user topic. Message payloads are
 * notification pings (ids only), never conversation content for third parties.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final com.connectly.community.ChannelAccessChecker channelAccess;

    public WebSocketConfig(JwtService jwtService, com.connectly.community.ChannelAccessChecker channelAccess) {
        this.jwtService = jwtService;
        this.channelAccess = channelAccess;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/ws");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("http://localhost:5173", "http://localhost:3000");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(acc.getCommand())) {
                    String auth = acc.getFirstNativeHeader("Authorization");
                    var claims = (auth != null && auth.startsWith("Bearer "))
                            ? jwtService.parse(auth.substring(7), JwtService.TYPE_ACCESS)
                            : java.util.Optional.<io.jsonwebtoken.Claims>empty();
                    if (claims.isEmpty()) {
                        throw new IllegalArgumentException("Invalid or missing bearer token");
                    }
                    // Principal name = user id, used for the own-topic check and delivery.
                    final String userId = claims.get().getSubject();
                    acc.setUser(() -> userId);
                } else if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
                    var principal = acc.getUser();
                    String dest = acc.getDestination();
                    if (principal == null || dest == null) {
                        throw new IllegalArgumentException("Subscription not allowed");
                    }
                    boolean ownNotifications = dest.equals("/topic/user/" + principal.getName());
                    // Voice rooms: public topics (peer lists) + per-user signal queues.
                    // Room membership is re-validated server-side when relaying.
                    boolean voiceTopic = dest.startsWith("/topic/voice/")
                            || dest.startsWith("/user/queue/voice/");
                    // Community channels carry member-only signals (typing), so the
                    // subscriber must actually belong to the channel's community.
                    boolean channelTopic = dest.startsWith("/topic/channel/");
                    if (channelTopic && !channelAccess.isMemberOfChannel(
                            parseUserId(principal.getName()), parseChannelId(dest))) {
                        throw new IllegalArgumentException("Not a member of this channel");
                    }
                    if (!ownNotifications && !voiceTopic && !channelTopic) {
                        throw new IllegalArgumentException("Subscription not allowed");
                    }
                }
                return message;
            }
        });
    }

    private static Long parseUserId(String principalName) {
        try {
            return Long.valueOf(principalName);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "/topic/channel/42" → 42, or null when the destination is malformed. */
    private static Long parseChannelId(String destination) {
        String tail = destination.substring("/topic/channel/".length());
        try {
            return Long.valueOf(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
