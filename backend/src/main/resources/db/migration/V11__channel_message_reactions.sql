-- Phase 11: emoji reactions on community channel messages.
-- Mirrors message_reactions (V10), which covers direct messages only.

CREATE TABLE channel_message_reactions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_message_id BIGINT      NOT NULL,
    user_id            BIGINT      NOT NULL,
    emoji              VARCHAR(16) NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_channel_reaction UNIQUE (channel_message_id, user_id, emoji),
    CONSTRAINT fk_channel_reaction_message FOREIGN KEY (channel_message_id)
        REFERENCES channel_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_channel_reaction_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_channel_reaction_message (channel_message_id)
) ENGINE = InnoDB;
