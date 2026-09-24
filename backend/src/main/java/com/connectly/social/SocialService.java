package com.connectly.social;

import com.connectly.common.error.ApiException;
import com.connectly.post.PostDtos;
import com.connectly.user.User;
import com.connectly.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SocialService {

    private final FollowRepository follows;
    private final ConnectionRepository connections;
    private final UserRepository users;
    private final com.connectly.user.UserProfileRepository profiles;
    private final com.connectly.notification.NotificationService notifier;

    public SocialService(FollowRepository follows, ConnectionRepository connections, UserRepository users,
                         com.connectly.user.UserProfileRepository profiles,
                         com.connectly.notification.NotificationService notifier) {
        this.follows = follows;
        this.connections = connections;
        this.users = users;
        this.profiles = profiles;
        this.notifier = notifier;
    }

    // ---------- follows ----------

    @Transactional
    public void follow(User actor, long followeeId) {
        if (actor.getId().equals(followeeId)) {
            throw ApiException.badRequest("You cannot follow yourself");
        }
        User followee = users.findById(followeeId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (follows.existsByFollowerIdAndFolloweeId(actor.getId(), followeeId)) return;
        Follow f = new Follow();
        f.setFollower(actor);
        f.setFollowee(followee);
        follows.save(f);
        notifier.notify(followee, actor, com.connectly.notification.NotificationService.FOLLOW, "user", followeeId);
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

    @Transactional(readOnly = true)
    public java.util.List<ConnectionDto> listConnections(User actor, String filter) {
        java.util.function.Function<Connection, ConnectionDto> map = c -> {
            boolean outgoing = c.getSender().getId().equals(actor.getId());
            User other = outgoing ? c.getReceiver() : c.getSender();
            com.connectly.user.UserProfile profile = profiles.findByUserId(other.getId()).orElse(null);
            return ConnectionDto.from(c, actor.getId(), profile);
        };
        if ("incoming".equals(filter)) {
            return connections.findByReceiverIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.PENDING)
                    .stream().map(map).toList();
        }
        if ("outgoing".equals(filter)) {
            return connections.findBySenderIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.PENDING)
                    .stream().map(map).toList();
        }
        return java.util.stream.Stream
                .concat(connections.findBySenderIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.ACCEPTED).stream(),
                        connections.findByReceiverIdAndStatusOrderByCreatedAtDesc(actor.getId(), Connection.Status.ACCEPTED).stream())
                .map(map)
                .toList();
    }
}
