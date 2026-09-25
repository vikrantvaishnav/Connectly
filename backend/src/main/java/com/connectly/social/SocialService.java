package com.connectly.social;

import com.connectly.common.error.ApiException;
import com.connectly.post.PostDtos;
import com.connectly.user.User;
import com.connectly.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SocialService {

    private final FollowRepository follows;
    private final ConnectionRepository connections;
    private final UserRepository users;
    private final com.connectly.user.UserProfileRepository profiles;
    private final com.connectly.notification.NotificationService notifier;
    private final SafetyService safety;

    public SocialService(FollowRepository follows, ConnectionRepository connections, UserRepository users,
                         com.connectly.user.UserProfileRepository profiles,
                         com.connectly.notification.NotificationService notifier,
                         SafetyService safety) {
        this.follows = follows;
        this.connections = connections;
        this.users = users;
        this.profiles = profiles;
        this.notifier = notifier;
        this.safety = safety;
    }

    // ---------- follows ----------

    @Transactional
    public String follow(User actor, long followeeId) {
        if (actor.getId().equals(followeeId)) {
            throw ApiException.badRequest("You cannot follow yourself");
        }
        // A block in either direction hides both accounts from each other.
        safety.requireNotBlocked(actor.getId(), followeeId);
        User followee = users.findById(followeeId).orElseThrow(() -> ApiException.notFound("User not found"));
        var existing = follows.findByFollowerIdAndFolloweeId(actor.getId(), followeeId);
        if (existing.isPresent()) {
            throw ApiException.conflict(existing.get().getStatus() == Follow.Status.PENDING
                    ? "Follow request already pending"
                    : "Already following");
        }
        Follow f = new Follow();
        f.setFollower(actor);
        f.setFollowee(followee);
        if (followee.isAccountPrivate()) {
            // Private account: the follow becomes a request the owner approves.
            f.setStatus(Follow.Status.PENDING);
            follows.save(f);
            notifier.notify(followee, actor,
                    com.connectly.notification.NotificationService.FOLLOW_REQUEST, "user", followeeId);
            return "PENDING";
        }
        f.setStatus(Follow.Status.ACTIVE);
        follows.save(f);
        notifier.notify(followee, actor, com.connectly.notification.NotificationService.FOLLOW, "user", followeeId);
        return "ACTIVE";
    }

    /** Instagram-style request card for the requests UI. */
    public record FollowRequestDto(long id, long userId, String username,
                                   String firstName, String lastName, String image) {}

    /** Follow requests waiting on this account's approval. */
    @Transactional(readOnly = true)
    public List<FollowRequestDto> followRequests(User me) {
        return follows.findByFolloweeIdAndStatusOrderByCreatedAtDesc(me.getId(), Follow.Status.PENDING).stream()
                .map(f -> {
                    com.connectly.user.UserProfile p = profiles.findByUserId(f.getFollower().getId()).orElse(null);
                    return new FollowRequestDto(f.getId(), f.getFollower().getId(), f.getFollower().getUsername(),
                            p != null ? p.getFirstName() : null, p != null ? p.getLastName() : null,
                            p != null ? p.getProfileImage() : null);
                })
                .toList();
    }

    /** Approve a pending follow request on my (private) account. */
    @Transactional
    public void acceptFollowRequest(User me, long requestId) {
        Follow f = follows.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!f.getFollowee().getId().equals(me.getId()) || f.getStatus() != Follow.Status.PENDING) {
            throw ApiException.notFound("Request not found");
        }
        f.setStatus(Follow.Status.ACTIVE);
        follows.save(f);
        notifier.notify(f.getFollower(), me,
                com.connectly.notification.NotificationService.FOLLOW_ACCEPTED, "user", me.getId());
    }

    /** Decline a request sent to me, or cancel one I sent. Pending only. */
    @Transactional
    public void removeFollowRequest(User me, long requestId) {
        Follow f = follows.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found"));
        boolean involved = f.getFollowee().getId().equals(me.getId()) || f.getFollower().getId().equals(me.getId());
        if (!involved || f.getStatus() != Follow.Status.PENDING) {
            throw ApiException.notFound("Request not found");
        }
        follows.delete(f);
    }

    @Transactional
    public void unfollow(User actor, long followeeId) {
        follows.findByFollowerIdAndFolloweeId(actor.getId(), followeeId).ifPresent(follows::delete);
    }

    // ---------- connections ----------

    /**
     * State machine for POST /connections/{userId}:
     *  - no row           → new PENDING request
     *  - row PENDING
     *      - i'm sender   → no-op (already requested)
     *      - i'm receiver → ACCEPT (mutual consent; also covers "they added me first")
     *  - row ACCEPTED     → no-op (already connected)
     *  - row DECLINED
     *      - declined by me → re-request allowed (fresh PENDING)
     *      - declined by them → still allowed after their decline; re-request sets PENDING again
     */
    @Transactional
    public Connection.Status sendOrAccept(User actor, long targetId) {
        if (actor.getId().equals(targetId)) {
            throw ApiException.badRequest("You cannot connect with yourself");
        }
        User target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found"));
        // Blocked pairs can never (re)connect — blocking tears the row down and
        // this gate keeps it torn down.
        safety.requireNotBlocked(actor.getId(), targetId);

        Connection c = connections.findBetween(actor.getId(), targetId).orElse(null);
        if (c == null) {
            Connection fresh = new Connection();
            fresh.setSender(actor);
            fresh.setReceiver(target);
            fresh.setStatus(Connection.Status.PENDING);
            connections.save(fresh);
            notifier.notify(target, actor,
                    com.connectly.notification.NotificationService.CONNECTION_REQUEST, "user", targetId);
            return Connection.Status.PENDING;
        }
        switch (c.getStatus()) {
            case PENDING -> {
                // mutual-consent accept: the RECEIVER posting back accepts the request
                if (c.getReceiver().getId().equals(actor.getId())) {
                    c.setStatus(Connection.Status.ACCEPTED);
                    connections.save(c);
                    return Connection.Status.ACCEPTED;
                }
                return Connection.Status.PENDING; // sender re-POSTing = already requested
            }
            case ACCEPTED -> {
                return Connection.Status.ACCEPTED; // already connected
            }
            case DECLINED -> {
                c.setStatus(Connection.Status.PENDING);
                c.setSender(actor); // the requester is whoever acts on a declined pair
                c.setReceiver(target);
                connections.save(c);
                return Connection.Status.PENDING;
            }
            default -> throw ApiException.conflict("Unexpected connection state");
        }
    }

    /** Receiver accepts a pending request. Sender identity checked — receiver-only action. */
    @Transactional
    public void accept(User actor, long connectionId) {
        Connection c = connections.findById(connectionId).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!c.getReceiver().getId().equals(actor.getId())) {
            throw ApiException.forbidden("Only the recipient can accept this request");
        }
        if (c.getStatus() != Connection.Status.PENDING) {
            throw ApiException.badRequest("This request is not pending");
        }
        c.setStatus(Connection.Status.ACCEPTED);
        notifier.notify(c.getSender(), actor,
                com.connectly.notification.NotificationService.CONNECTION_ACCEPTED, "user", c.getSender().getId());
    }

    /** Receiver declines; sender may also cancel their own pending request this way. */
    @Transactional
    public void declineOrCancel(User actor, long connectionId) {
        Connection c = connections.findById(connectionId).orElseThrow(() -> ApiException.notFound("Request not found"));
        boolean receiver = c.getReceiver().getId().equals(actor.getId());
        boolean sender = c.getSender().getId().equals(actor.getId());
        if (!receiver && !sender) {
            throw ApiException.forbidden("Not your request");
        }
        if (c.getStatus() == Connection.Status.PENDING) {
            if (receiver) {
                c.setStatus(Connection.Status.DECLINED);
            } else {
                connections.delete(c); // sender cancelling just removes it
            }
        } else if (c.getStatus() == Connection.Status.ACCEPTED) {
            connections.delete(c); // unfriend
        }
    }

    public boolean isConnected(User a, User b) {
        return connections.findBetween(a.getId(), b.getId())
                .map(c -> c.getStatus() == Connection.Status.ACCEPTED)
                .orElse(false);
    }

    public record ConnectionDto(long id, PostDtos.AuthorDto user, String direction,
                                String status, Instant createdAt) {
        public static ConnectionDto from(Connection c, long viewerId, com.connectly.user.UserProfile profile) {
            boolean outgoing = c.getSender().getId().equals(viewerId);
            User other = outgoing ? c.getReceiver() : c.getSender();
            return new ConnectionDto(c.getId(), PostDtos.AuthorDto.from(other, profile),
                    outgoing ? "outgoing" : "incoming", c.getStatus().name(), c.getCreatedAt());
        }
    }

    /** Badge counts for the Requests/Matches inbox — 3 cheap count queries. */
    public record Summary(long incoming, long outgoing, long matches) {}

    @Transactional(readOnly = true)
    public Summary summary(User actor) {
        return new Summary(
                connections.countByReceiverIdAndStatus(actor.getId(), Connection.Status.PENDING),
                connections.countBySenderIdAndStatus(actor.getId(), Connection.Status.PENDING),
                connections.countByReceiverIdAndStatus(actor.getId(), Connection.Status.ACCEPTED)
                        + connections.countBySenderIdAndStatus(actor.getId(), Connection.Status.ACCEPTED));
    }

    @Transactional(readOnly = true)
    public java.util.List<ConnectionDto> listConnections(User actor, String filter) {
        java.util.List<Connection> rows;
        if ("incoming".equals(filter)) {
            rows = connections.findByReceiverIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.PENDING);
        } else if ("outgoing".equals(filter)) {
            rows = connections.findBySenderIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.PENDING);
        } else {
            rows = java.util.stream.Stream
                    .concat(connections.findBySenderIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.ACCEPTED).stream(),
                            connections.findByReceiverIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.ACCEPTED).stream())
                    .toList();
        }
        // One batched profile load for the whole page (was a query per connection).
        java.util.List<Long> otherIds = rows.stream()
                .map(c -> c.getSender().getId().equals(actor.getId()) ? c.getReceiver().getId() : c.getSender().getId())
                .distinct()
                .toList();
        java.util.Map<Long, com.connectly.user.UserProfile> profileById = new java.util.HashMap<>();
        if (!otherIds.isEmpty()) {
            for (com.connectly.user.UserProfile p : profiles.findByUserIdIn(otherIds)) {
                profileById.put(p.getUserId(), p);
            }
        }
        return rows.stream().map(c -> {
            boolean outgoing = c.getSender().getId().equals(actor.getId());
            User other = outgoing ? c.getReceiver() : c.getSender();
            return ConnectionDto.from(c, actor.getId(), profileById.get(other.getId()));
        }).toList();
    }
}
