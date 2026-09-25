package com.connectly.presence;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Lightweight "how many people are active right now" counter for the UI. */
@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @GetMapping
    public Map<String, Object> online() {
        return Map.of("online", presence.onlineCount());
    }
}
