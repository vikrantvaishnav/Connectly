package com.connectly.notification;

import com.connectly.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationService.NotificationView> list(@AuthenticationPrincipal User me,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "30") int size) {
        return notifications.list(me, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User me) {
        return Map.of("count", notifications.unreadCount(me));
    }

    @PostMapping("/read-all")
    public Map<String, Object> markAllRead(@AuthenticationPrincipal User me) {
        notifications.markAllRead(me);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(@AuthenticationPrincipal User me, @PathVariable Long id) {
        notifications.markRead(me, id);
        return Map.of("ok", true);
    }
}
