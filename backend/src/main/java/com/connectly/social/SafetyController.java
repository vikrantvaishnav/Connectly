package com.connectly.social;

import com.connectly.security.RateLimiter;
import com.connectly.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Safety endpoints: block, unblock, mute, unmute and the blocked/muted lists.
 * Enforcement lives in SafetyService and is applied by every read/write path.
 */
@RestController
@RequestMapping("/api/v1")
public class SafetyController {

    private final SafetyService safety;
    private final RateLimiter rateLimiter;

    public SafetyController(SafetyService safety, RateLimiter rateLimiter) {
        this.safety = safety;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal User me, @PathVariable long id) {
        if (!rateLimiter.allow("block:" + me.getUsername(), 30, 60)) {
            throw com.connectly.common.error.ApiException.tooManyRequests("Too many requests, slow down");
        }
        safety.block(me, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/block")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal User me, @PathVariable long id) {
        safety.unblock(me, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/mute")
    public ResponseEntity<Void> mute(@AuthenticationPrincipal User me, @PathVariable long id) {
        safety.mute(me, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/mute")
    public ResponseEntity<Void> unmute(@AuthenticationPrincipal User me, @PathVariable long id) {
        safety.unmute(me, id);
        return ResponseEntity.noContent().build();
    }

    /** Blocked + muted lists for the Settings screen. */
    @GetMapping("/safety/lists")
    public Map<String, List<SafetyService.SafetyEntry>> lists(@AuthenticationPrincipal User me) {
        return safety.lists(me);
    }
}
