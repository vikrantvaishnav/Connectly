package com.connectly.chat;

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
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    @Column(length = 500)
    private String lastMessage;

    private Instant lastMessageAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public User getUserA() { return userA; }
    public void setUserA(User userA) { this.userA = userA; }
    public User getUserB() { return userB; }
    public void setUserB(User userB) { this.userB = userB; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the other participant of the conversation relative to the given user. */
    public User otherOf(User u) {
        return userA.getId().equals(u.getId()) ? userB : userA;
    }

    public boolean involves(User u) {
        return userA.getId().equals(u.getId()) || userB.getId().equals(u.getId());
    }
}
