package com.connectly.social;

import com.connectly.chat.ConversationRepository;
import com.connectly.common.error.ApiException;
import com.connectly.notification.NotificationRepository;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blocks and mutes — the enforcement heart of the safety matrix.
 *
 * Semantics:
 *  - BLOCK: total, both directions. No posts/feed/explore/nearby visibility,
 *    no DMs, no follows, no connection requests, no notifications. Blocking
 *    also tears the existing relationship down (unfollow both ways, delete
 *    any connection row, remove the shared DM conversation).
 *  - MUTE: soft, one direction. The muted user notices nothing; the muter
 *    just stops seeing their content.
 *
 * Every check here is server-side and batched so page renders stay O(1)
 * queries, not O(n).
 */
@Service
public class SafetyService {

    private final UserBlockRepository blocks;
    private final UserMuteRepository mutes;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FollowRepository follows;
    private final ConnectionRepository connections;
    private final ConversationRepository conversations;
    private final NotificationRepository notifications;

    public SafetyService(UserBlockRepository blocks, UserMuteRepository mutes,
                         UserRepository users, UserProfileRepository profiles,
                         FollowRepository follows, ConnectionRepository connections,
                         ConversationRepository conversations, NotificationRepository notifications) {
        this.blocks = blocks;
        this.mutes = mutes;
        this.users = users;
        this.profiles = profiles;
        this.follows = follows;
        this.connections = connections;
        this.conversations = conversations;
        this.notifications = notifications;
    }

    // ---------- queries used across services ----------

    /** True when a and b are blocked in either direction. */
    @Transactional(readOnly = true)
    public boolean blockedBetween(long a, long b) {
        return blocks.existsEitherDirection(a, b);
    }

    /** Throws 404 when blocked — callers use it as a visibility gate. */
    @Transactional(readOnly = true)
    public void requireNotBlocked(long viewerId, long otherId) {
        if (viewerId != otherId && blocks.existsEitherDirection(viewerId, otherId)) {
            throw ApiException.notFound("Not found");
        }
    }

    /** One query per page: map of otherUserId → true where a block involves me. */
    @Transactional(readOnly = true)
    public Set<Long> blockedOthersAmong(long me, List<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(blocks.findBlockedOthersAmong(me, ids));
    }

    /** One query per page: which of these ids does the viewer mute? */
    @Transactional(readOnly = true)
    public Set<Long> mutedIdsAmong(long me, List<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(mutes.findMutedIdsAmong(me, ids));
    }

    /**
     * Every author id whose content {@code viewerId} must never see: users they
     * block, users who block them, and users they muted. Two queries, however
     * large the graph. Used by feed/explore ranking and discovery surfaces.
     */
    @Transactional(readOnly = true)
    public Set<Long> hiddenAuthorIds(long viewerId) {
        if (viewerId <= 0) return Set.of();
        Set<Long> hidden = new HashSet<>(blocks.blockedIdsInvolving(viewerId));
        mutes.findByMuterIdOrderByCreatedAtDesc(viewerId).forEach(m -> hidden.add(m.getMuted().getId()));
        hidden.remove(viewerId);
        return hidden;
    }

    // ---------- mutations ----------

    /** Blocks the target and tears down every relationship between the two. */
    @Transactional
    public void block(User me, long targetId) {
        if (me.getId() == targetId) throw ApiException.badRequest("You cannot block yourself");
        User target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found"));

        if (!blocks.existsByBlockerIdAndBlockedId(me.getId(), targetId)) {
            UserBlock b = new UserBlock();
            b.setBlocker(me);
            b.setBlocked(target);
            blocks.save(b);
        }

        // Relationship teardown, both directions.
        follows.findByFollowerIdAndFolloweeId(me.getId(), targetId).ifPresent(follows::delete);
        follows.findByFollowerIdAndFolloweeId(targetId, me.getId()).ifPresent(follows::delete);
        connections.findBetween(me.getId(), targetId).ifPresent(connections::delete);
        conversations.findBetween(me.getId(), targetId).ifPresent(conversations::delete);

        // Neither side needs historical pings about the other.
        notifications.deleteAllForUser(targetId, me.getId());
        notifications.deleteAllForUser(me.getId(), targetId);
    }

    @Transactional
    public void unblock(User me, long targetId) {
        blocks.findByBlockerIdAndBlockedId(me.getId(), targetId).ifPresent(blocks::delete);
    }

    @Transactional
    public void mute(User me, long targetId) {
        if (me.getId() == targetId) throw ApiException.badRequest("You cannot mute yourself");
        if (blocks.existsEitherDirection(me.getId(), targetId)) {
            throw ApiException.badRequest("Blocked users are hidden already");
        }
        User target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!mutes.existsByMuterIdAndMutedId(me.getId(), targetId)) {
            UserMute m = new UserMute();
            m.setMuter(me);
            m.setMuted(target);
            mutes.save(m);
        }
    }

    @Transactional
    public void unmute(User me, long targetId) {
        mutes.findByMuterIdAndMutedId(me.getId(), targetId).ifPresent(mutes::delete);
    }

    // ---------- views ----------

    public record SafetyEntry(long id, String username, String firstName, String lastName,
                              String profileImage, String createdAt, String kind) {}

    @Transactional(readOnly = true)
    public Map<String, List<SafetyEntry>> lists(User me) {
        Map<Long, UserProfile> profileById = new HashMap<>();
        java.util.function.Function<Long, UserProfile> prof = id -> profileById.computeIfAbsent(id,
                uid -> profiles.findByUserId(uid).orElse(null));

        var blocked = blocks.findByBlockerIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(b -> toEntry(b.getBlocked(), prof.apply(b.getBlocked().getId()), "blocked"))
                .toList();
        var muted = mutes.findByMuterIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(m -> toEntry(m.getMuted(), prof.apply(m.getMuted().getId()), "muted"))
                .toList();
        return Map.of("blocked", blocked, "muted", muted);
    }

    private SafetyEntry toEntry(User u, com.connectly.user.UserProfile p, String kind) {
        String name = p == null ? null
                : java.util.stream.Stream.of(p.getFirstName(), p.getLastName())
                        .filter(s -> s != null && !s.isBlank())
                        .collect(java.util.stream.Collectors.joining(" "));
        return new SafetyEntry(u.getId(), u.getUsername(),
                p != null ? p.getFirstName() : null,
                p != null ? p.getLastName() : null,
                p != null ? p.getProfileImage() : null,
                name == null || name.isBlank() ? u.getUsername() : name,
                kind);
    }
}
