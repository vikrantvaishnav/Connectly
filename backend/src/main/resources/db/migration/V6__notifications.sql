-- Phase 5: notifications + post images.
CREATE TABLE notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    actor_id     BIGINT NULL,          -- who triggered it (null for system notices)
    type         VARCHAR(30) NOT NULL, -- LIKE, COMMENT, FOLLOW, CONNECTION_REQUEST, CONNECTION_ACCEPTED, MESSAGE
    entity_type  VARCHAR(20) NULL,     -- post, comment, conversation, user
    entity_id    BIGINT NULL,
    read_flag    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_notif_recipient (recipient_id, read_flag, id)
) ENGINE = InnoDB;

-- Optional image attachment on posts (URL returned by the media upload endpoint).
ALTER TABLE posts ADD COLUMN image_url VARCHAR(300) NULL;
