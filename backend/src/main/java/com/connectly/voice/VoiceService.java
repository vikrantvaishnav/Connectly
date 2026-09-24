package com.connectly.voice;

import com.connectly.chat.ChatPusher;
import com.connectly.common.error.ApiException;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceService {

    private final VoiceRoomRepository rooms;
    private final VoiceParticipantRepository participants;
    private final UserProfileRepository profiles;
    private final SimpMessagingTemplate broker;

    public VoiceService(VoiceRoomRepository rooms, VoiceParticipantRepository participants,
                        UserProfileRepository profiles, SimpMessagingTemplate broker) {
        this.rooms = rooms;
        this.participants = participants;
        this.profiles = profiles;
        this.broker = broker;
    }

    // ---- DTOs ----

    public record RoomView(long id, String name, String hostUsername, String status, int participantCount, Instant createdAt, Long communityId) {}

    public record ParticipantView(long userId, String username, String name, boolean muted) {}

    private String nameOf(User u) {
        UserProfile p = profiles.findByUserId(u.getId()).orElse(null);
        String n = p == null ? null
                : java.util.stream.Stream.of(p.getFirstName(), p.getLastName())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
        return (n == null || n.isBlank()) ? u.getUsername() : n;
    }

    private ParticipantView toParticipantView(VoiceParticipant vp) {
        return new ParticipantView(vp.getUser().getId(), vp.getUser().getUsername(), nameOf(vp.getUser()), vp.isMuted());
    }

    private RoomView toRoomView(VoiceRoom r) {
        int count = participants.findByRoomId(r.getId()).size();
        return new RoomView(r.getId(), r.getName(), r.getHost().getUsername(),
                r.getStatus().name(), count, r.getCreatedAt(), r.getCommunityId());
    }

    /** Broadcasts the current participant list to everyone in the room topic. */
    private void broadcastPeers(VoiceRoom room) {
        List<ParticipantView> peers = participants.findByRoomId(room.getId()).stream()
                .map(this::toParticipantView)
                .toList();
        broker.convertAndSend("/topic/voice/" + room.getId(), peers);
    }

    // ---- room lifecycle ----

    @Transactional
    public RoomView create(User me, String name, Long communityId) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() < 2 || trimmed.length() > 60) {
            throw ApiException.badRequest("Room name must be 2-60 characters");
        }
        VoiceRoom r = new VoiceRoom();
        r.setName(trimmed);
        r.setHost(me);
        r.setCommunityId(communityId);
        r = rooms.save(r);
        return toRoomView(r);
    }

    @Transactional(readOnly = true)
    public List<RoomView> listOpen() {
        List<VoiceRoom> open = rooms.findByStatusOrderByCreatedAtDesc(VoiceRoom.Status.OPEN);
        if (open.isEmpty()) return List.of();
        // batch participant counts (1 query instead of N)
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : participants.countByRoomIdIn(open.stream().map(VoiceRoom::getId).toList())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return open.stream()
                .map(r -> new RoomView(r.getId(), r.getName(), r.getHost().getUsername(),
                        r.getStatus().name(), counts.getOrDefault(r.getId(), 0L).intValue(),
                        r.getCreatedAt(), r.getCommunityId()))
                .toList();
    }

    @Transactional
    public RoomView close(User me, long roomId) {
        VoiceRoom r = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        if (!r.getHost().getId().equals(me.getId()) && !"ADMIN".equals(me.getRole().name())) {
            throw ApiException.forbidden("Only the host can close the room");
        }
        r.setStatus(VoiceRoom.Status.CLOSED);
        r.setClosedAt(Instant.now());
        broadcastPeers(r); // tells remaining clients the room is now empty/closed
        return toRoomView(r);
    }

    // ---- presence ----

    @Transactional
    public List<ParticipantView> join(User me, long roomId) {
        VoiceRoom r = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        if (r.getStatus() != VoiceRoom.Status.OPEN) {
            throw ApiException.badRequest("Room is closed");
        }
        if (!participants.existsByRoomIdAndUserId(roomId, me.getId())) {
            VoiceParticipant vp = new VoiceParticipant();
            vp.setRoom(r);
            vp.setUser(me);
            participants.save(vp);
        }
        broadcastPeers(r);
        return participants.findByRoomId(r.getId()).stream().map(this::toParticipantView).toList();
    }

    @Transactional
    public void leave(User me, long roomId) {
        VoiceRoom r = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        participants.findByRoomIdAndUserId(roomId, me.getId()).ifPresent(participants::delete);
        broadcastPeers(r);
    }

    @Transactional
    public List<ParticipantView> setMuted(User me, long roomId, boolean muted) {
        VoiceRoom r = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        VoiceParticipant vp = participants.findByRoomIdAndUserId(roomId, me.getId())
                .orElseThrow(() -> ApiException.notFound("Not in this room"));
        vp.setMuted(muted);
        participants.save(vp);
        broadcastPeers(r);
        return participants.findByRoomId(r.getId()).stream().map(this::toParticipantView).toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> peers(User me, long roomId) {
        VoiceRoom r = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        if (!participants.existsByRoomIdAndUserId(roomId, me.getId())) {
            throw ApiException.notFound("Not in this room");
        }
        return participants.findByRoomId(r.getId()).stream().map(this::toParticipantView).toList();
    }
}
