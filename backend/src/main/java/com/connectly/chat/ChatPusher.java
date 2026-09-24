package com.connectly.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes lightweight "new message" pings to a user's private topic.
 * Payload carries ids only — the client refetches over the authorized REST API.
 */
@Component
public class ChatPusher {

    private final SimpMessagingTemplate broker;

    public ChatPusher(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public record NewMessagePing(Long conversationId, Long messageId, Long senderId, String senderUsername) {}

    public record NotificationPing(Long notificationId, String type) {}

    public void pushNewMessage(Long recipientUserId, Long conversationId, Long messageId,
                               Long senderId, String senderUsername) {
        broker.convertAndSend("/topic/user/" + recipientUserId,
                new NewMessagePing(conversationId, messageId, senderId, senderUsername));
    }

    public void pushNotification(Long recipientUserId, Long notificationId, String type) {
        broker.convertAndSend("/topic/user/" + recipientUserId,
                new NotificationPing(notificationId, type));
    }
}
