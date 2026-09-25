-- Phase 9: dating-style profile fields + Discord-style message reactions.
--
-- user_profiles already carries profile_image/cover_image/date_of_birth from V1
-- (they were just never mapped). This migration adds the free-text "prompt"
-- style fields that dating apps (Hinge/Bumble) use to seed conversations.

ALTER TABLE user_profiles ADD COLUMN interests VARCHAR(300) NULL;
ALTER TABLE user_profiles ADD COLUMN looking_for VARCHAR(60) NULL;

-- Emoji reactions on direct messages: one row per (message, user, emoji).
CREATE TABLE message_reactions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(16) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_reaction UNIQUE (message_id, user_id, emoji),
    CONSTRAINT fk_reaction_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_reaction_message (message_id)
) ENGINE = InnoDB;
