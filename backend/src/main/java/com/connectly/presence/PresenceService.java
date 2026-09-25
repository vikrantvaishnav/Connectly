package com.connectly.presence;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory "who is active right now" registry — the green dot in Discord and on
 * every dating app's cards.
 *
 * Deliberately NOT a database table: presence is ephemeral, high-churn, and has
 * no audit value. A single Render instance means a plain map is exact; behind a
 * load balancer this would become Redis, so every read goes through this class.
 *
 * Users are marked online by any authenticated REST request (see JwtAuthFilter)
 * or by holding a WebSocket connection, and expire after {@link #TTL} of silence.
 */
@Service
public class PresenceService {

    /** How long a user stays "online" after their last authenticated action. */
    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<Long, Instant> lastSeen = new ConcurrentHashMap<>();
    /** Live WebSocket connections per user (a user may have several tabs/devices). */
    private final Map<Long, AtomicLong> sockets = new ConcurrentHashMap<>();

    /** Record activity for a user (called for every authenticated request). */
    public void touch(Long userId) {
        if (userId != null) {
            lastSeen.put(userId, Instant.now());
        }
    }

    /** A WebSocket connected for this user. */
    public void socketOpened(Long userId) {
        if (userId == null) return;
        sockets.computeIfAbsent(userId, k -> new AtomicLong()).incrementAndGet();
        touch(userId);
    }

    /** A WebSocket closed for this user; presence lingers until the TTL expires. */
    public void socketClosed(Long userId) {
        if (userId == null) return;
        AtomicLong n = sockets.get(userId);
        if (n != null && n.decrementAndGet() <= 0) {
            sockets.remove(userId);
        }
    }

    public boolean isOnline(Long userId) {
        if (userId == null) return false;
        if (sockets.containsKey(userId)) return true;
        Instant t = lastSeen.get(userId);
        return t != null && t.isAfter(Instant.now().minus(TTL));
    }

    /** Batch check used by list/card endpoints so a page costs one lookup, not N. */
    public Set<Long> onlineAmong(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Set.of();
        Set<Long> online = new HashSet<>();
        for (Long id : userIds) {
            if (isOnline(id)) online.add(id);
        }
        return online;
    }

    public int onlineCount() {
        int n = 0;
        for (Long id : lastSeen.keySet()) {
            if (isOnline(id)) n++;
        }
        return n;
    }
}
