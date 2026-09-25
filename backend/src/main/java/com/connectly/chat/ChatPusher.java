package com.connectly.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes lightweight pings to a user's private topic. Payloads carry ids only —
 * the client refetches over the authorized REST API, so a forged ping can never
 * inject content into someone's chat.
 *
 * Every payload carries a {@code type} so clients can branch (and ignore types
 * they don't understand yet, which keeps old clients working).
 */
@Component
public class ChatPusher {

    private final SimpMessagingTemplate broker;

    public ChatPusher(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public record NewMessagePing(String type, Long conversationId, Long messageId,
                                 Long senderId, String senderUsername) {
        public static NewMessagePing of(Long conversationId, Long messageId, Long senderId, String senderUsername) {
            return new NewMessagePing("message", conversationId, messageId, senderId, senderUsername);
        }
    }

    public record NotificationPing(String type, Long notificationId, String notificationType) {
        public static NotificationPing of(Long notificationId, String notificationType) {
            return new NotificationPing("notification", notificationId, notificationType);
        }
    }

    /** "Someone is typing" — ephemeral, never persisted (Discord behaviour). */
    public record TypingPing(String type, Long conversationId, Long userId, String username) {
        public static TypingPing of(Long conversationId, Long userId, String username) {
            return new TypingPing("typing", conversationId, userId, username);
        }
    }

    /** A reaction changed; the client refetches the conversation's messages. */
    public record ReactionPing(String type, Long conversationId, Long messageId) {
        public static ReactionPing of(Long conversationId, Long messageId) {
            return new ReactionPing("reaction", conversationId, messageId);
        }
    }

    public void pushNewMessage(Long recipientUserId, Long conversationId, Long messageId,
                               Long senderId, String senderUsername) {
        broker.convertAndSend("/topic/user/" + recipientUserId,
                NewMessagePing.of(conversationId, messageId, senderId, senderUsername));
    }

    public void pushNotification(Long recipientUserId, Long notificationId, String type) {
        broker.convertAndSend("/topic/user/" + recipientUserId, NotificationPing.of(notificationId, type));
    }

    public void pushTyping(Long recipientUserId, Long conversationId, Long senderId, String senderUsername) {
        broker.convertAndSend("/topic/user/" + recipientUserId,
                TypingPing.of(conversationId, senderId, senderUsername));
    }

    public void pushReaction(Long recipientUserId, Long conversationId, Long messageId) {
        broker.convertAndSend("/topic/user/" + recipientUserId, ReactionPing.of(conversationId, messageId));
    }
}
