-- Safety layer: blocks and mutes between users, plus audit handling for
-- self-service account deletion.
--
-- A block is total and bidirectional in effect: neither user can see, message,
-- follow, request, react to, or discover the other (posts, feed, explore,
-- nearby, DMs, notifications). Mutes are one-directional and soft: the muted
-- user can still interact with you, you just stop seeing their content.

CREATE TABLE user_blocks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id  BIGINT    NOT NULL,
    blocked_id  BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_blocks UNIQUE (blocker_id, blocked_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_blocks_blocker (blocker_id),
    INDEX idx_blocks_blocked (blocked_id)
) ENGINE = InnoDB;

CREATE TABLE user_mutes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    muter_id    BIGINT    NOT NULL,
    muted_id    BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_mutes UNIQUE (muter_id, muted_id),
    CONSTRAINT fk_mutes_muter FOREIGN KEY (muter_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mutes_muted FOREIGN KEY (muted_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_mutes_muter (muter_id)
) ENGINE = InnoDB;

-- When a user deletes their own account, users.status becomes DELETED and the
-- row is removed by the service in the same transaction; audit_logs.user_id is
-- detached (that table has no FK by design) so moderation history survives.
ALTER TABLE audit_logs ADD COLUMN actor_label VARCHAR(120) NULL;
