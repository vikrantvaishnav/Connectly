package com.connectly.voice;

import com.connectly.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "voice_participants",
       uniqueConstraints = @UniqueConstraint(name = "uq_voice_participant", columnNames = {"room_id", "user_id"}))
public class VoiceParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private VoiceRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(nullable = false)
    private boolean muted = false;

    public Long getId() { return id; }
    public VoiceRoom getRoom() { return room; }
    public void setRoom(VoiceRoom room) { this.room = room; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getJoinedAt() { return joinedAt; }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
}
