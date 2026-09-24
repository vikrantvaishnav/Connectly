package com.connectly.security;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter keyed by arbitrary string (e.g. "login:ip:1.2.3.4").
 * In-memory for now; the API is deliberately simple so a Redis-backed implementation
 * can replace it for multi-instance deployments without touching call sites.
 */
@Service
public class RateLimiter {

    private static final class Window {
        final Deque<Long> hits = new ArrayDeque<>();
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Record a hit and decide whether it is allowed.
     *
     * @param key    bucket key
     * @param limit  max hits per window
     * @param windowSeconds window length
     * @return true if allowed, false if over the limit
     */
    public boolean allow(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            Deque<Long> hits = w.hits;
            while (!hits.isEmpty() && now - hits.peekFirst() > windowMs) {
                hits.pollFirst();
            }
            if (hits.size() >= limit) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }

    /** Parse configs like "10/60" → limit/window. Falls back to permissive on bad config. */
    public static int[] parseSpec(String spec) {
        try {
            String[] parts = spec.split("/");
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            return new int[]{1000, 60};
        }
    }
}
