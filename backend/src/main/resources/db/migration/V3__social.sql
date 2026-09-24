-- Phase 2: the social graph.

CREATE TABLE posts (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NULL,
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_posts_user_created (user_id, created_at DESC),
    INDEX idx_posts_created (created_at DESC)
) ENGINE=InnoDB;

CREATE TABLE post_likes (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_post_likes UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE comments (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id           BIGINT        NOT NULL,
    user_id           BIGINT        NOT NULL,
    content           VARCHAR(1000) NOT NULL,
    parent_comment_id BIGINT        NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_comment_id) REFERENCES comments (id) ON DELETE CASCADE,
    INDEX idx_comments_post (post_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE follows (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_follows UNIQUE (follower_id, followee_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_follows_followee (followee_id)
) ENGINE=InnoDB;

CREATE TABLE connections (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id   BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NULL,
    CONSTRAINT uq_connections_pair UNIQUE (sender_id, receiver_id),
    CONSTRAINT fk_connections_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_connections_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_connections_receiver (receiver_id, status)
) ENGINE=InnoDB;
