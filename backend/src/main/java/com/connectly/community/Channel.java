package com.connectly.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "channels",
       uniqueConstraints = @UniqueConstraint(name = "uq_channel_name", columnNames = {"community_id", "name"}))
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String topic;

    @Column(nullable = false)
    private int position = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Long getCommunityId() { return communityId; }
    public void setCommunityId(Long communityId) { this.communityId = communityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Instant getCreatedAt() { return createdAt; }
}
