package com.connectly.social;

import com.connectly.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SocialController {

    private final SocialService social;

    public SocialController(SocialService social) {
        this.social = social;
    }

    @PostMapping("/users/{userId}/follow")
    public ResponseEntity<Void> follow(@AuthenticationPrincipal User user, @PathVariable long userId) {
        social.follow(user, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal User user, @PathVariable long userId) {
        social.unfollow(user, userId);
        return ResponseEntity.noContent().build();
    }

    /** POST /connections/{userId} — send a request, or accept if they sent you one. */
    @PostMapping("/connections/{userId}")
    public Map<String, String> connect(@AuthenticationPrincipal User user, @PathVariable long userId) {
        Connection.Status status = social.sendOrAccept(user, userId);
        return Map.of("status", status.name());
    }

    @GetMapping("/connections")
    public List<SocialService.ConnectionDto> connections(@AuthenticationPrincipal User user,
                                                         @RequestParam(defaultValue = "all") String filter) {
        return social.listConnections(user, filter);
    }

    /** Badge counts for the Requests/Matches inbox in the nav. */
    @GetMapping("/connections/summary")
    public SocialService.Summary connectionsSummary(@AuthenticationPrincipal User user) {
        return social.summary(user);
    }

    @PostMapping("/connections/requests/{id}/accept")
    public ResponseEntity<Void> accept(@AuthenticationPrincipal User user, @PathVariable long id) {
        social.accept(user, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/connections/requests/{id}")
    public ResponseEntity<Void> declineOrCancel(@AuthenticationPrincipal User user, @PathVariable long id) {
        social.declineOrCancel(user, id);
        return ResponseEntity.noContent().build();
    }
}
