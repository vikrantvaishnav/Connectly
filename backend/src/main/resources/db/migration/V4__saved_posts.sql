-- Phase 2 completion: saved posts.

CREATE TABLE saved_posts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_saved_posts UNIQUE (post_id, user_id),
    CONSTRAINT fk_saved_posts_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_posts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_saved_posts_user (user_id, created_at DESC)
) ENGINE=InnoDB;
