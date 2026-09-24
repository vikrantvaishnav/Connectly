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

/** One-way follow edge: follower → followee. */
@Entity
@Table(name = "follows", uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "followee_id"}))
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public User getFollower() { return follower; }
    public void setFollower(User follower) { this.follower = follower; }
    public User getFollowee() { return followee; }
    public void setFollowee(User followee) { this.followee = followee; }
}
