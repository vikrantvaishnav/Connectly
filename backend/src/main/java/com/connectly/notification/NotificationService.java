package com.connectly.notification;

import com.connectly.chat.ChatPusher;
import com.connectly.common.error.ApiException;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    public static final String LIKE = "LIKE";
    public static final String COMMENT = "COMMENT";
    public static final String FOLLOW = "FOLLOW";
    public static final String CONNECTION_REQUEST = "CONNECTION_REQUEST";
    public static final String CONNECTION_ACCEPTED = "CONNECTION_ACCEPTED";
    public static final String MESSAGE = "MESSAGE";

    private final NotificationRepository notifications;
    private final UserProfileRepository profiles;
    private final ChatPusher pusher;

    public NotificationService(NotificationRepository notifications, UserProfileRepository profiles, ChatPusher pusher) {
        this.notifications = notifications;
        this.profiles = profiles;
        this.pusher = pusher;
    }

    public record NotificationView(Long id, String type, Long actorId, String actorUsername, String actorName,
                                   String entityType, Long entityId, boolean read, Instant createdAt) {}

    private NotificationView toView(Notification n) {
        String username = null;
        String name = null;
        if (n.getActor() != null) {
            username = n.getActor().getUsername();
            UserProfile p = profiles.findByUserId(n.getActor().getId()).orElse(null);
            name = p == null ? username
                    : java.util.stream.Stream.of(p.getFirstName(), p.getLastName())
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(" "));
            if (name == null || name.isBlank()) name = username;
        }
        return new NotificationView(n.getId(), n.getType(), n.getActor() == null ? null : n.getActor().getId(),
                username, name, n.getEntityType(), n.getEntityId(), n.isRead(), n.getCreatedAt());
    }

    /** Creates a notification and pushes a realtime ping. Never notifies the actor about themself. */
    @Transactional
    public void notify(User recipient, User actor, String type, String entityType, Long entityId) {
        if (actor != null && actor.getId().equals(recipient.getId())) {
            return; // never self-notify
        }
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setType(type);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n = notifications.save(n);
        pusher.pushNotification(recipient.getId(), n.getId(), type);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(User me, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return notifications.findByRecipientIdOrderByIdDesc(me.getId(), pageable).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User me) {
        return notifications.countByRecipientIdAndReadFalse(me.getId());
    }

    @Transactional
    public void markAllRead(User me) {
        notifications.markAllRead(me.getId());
    }

    @Transactional
    public void markRead(User me, Long id) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (!n.getRecipient().getId().equals(me.getId())) {
            throw ApiException.notFound("Notification not found"); // 404, never leak existence
        }
        n.setRead(true);
        notifications.save(n);
    }
}
