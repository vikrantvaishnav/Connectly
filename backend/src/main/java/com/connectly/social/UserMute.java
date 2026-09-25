package com.connectly.social;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A one-directional mute. Soft: the muted user can still interact with you,
 * you just stop seeing their content in your feeds and lists.
 */
@Entity
@Table(name = "user_mutes",
       uniqueConstraints = @UniqueConstraint(name = "uq_user_mutes", columnNames = {"muter_id", "muted_id"}))
public class UserMute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muter_id", nullable = false)
    private User muter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muted_id", nullable = false)
    private User muted;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public User getMuter() { return muter; }
    public void setMuter(User muter) { this.muter = muter; }
    public User getMuted() { return muted; }
    public void setMuted(User muted) { this.muted = muted; }
    public Instant getCreatedAt() { return createdAt; }
}
