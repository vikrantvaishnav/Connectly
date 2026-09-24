-- Phase 4: real-time chat.
-- Conversations are 1:1 DMs: a conversation row stores the two users,
-- plus a denormalized "last message" cache for fast inbox listing.
CREATE TABLE conversations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_a_id       BIGINT NOT NULL,
    user_b_id       BIGINT NOT NULL,
    last_message    VARCHAR(500) NULL,
    last_message_at TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_conversation_pair UNIQUE (user_a_id, user_b_id),
    CONSTRAINT fk_conv_a FOREIGN KEY (user_a_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_b FOREIGN KEY (user_b_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_conv_a (user_a_id, last_message_at),
    INDEX idx_conv_b (user_b_id, last_message_at)
) ENGINE = InnoDB;

CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id       BIGINT NOT NULL,
    content         VARCHAR(4000) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP NULL,
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_msg_conv (conversation_id, id)
) ENGINE = InnoDB;

-- Per-participant unread tracking: each side remembers where it has read up to.
CREATE TABLE conversation_states (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_conv_state UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_cs_conv FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_cs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
