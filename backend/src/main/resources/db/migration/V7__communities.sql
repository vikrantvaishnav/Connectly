-- Phase 6: communities (servers) with text channels, like Discord.
CREATE TABLE communities (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    slug        VARCHAR(60) NOT NULL,
    description VARCHAR(500) NULL,
    icon        VARCHAR(8) NULL,
    owner_id    BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_community_slug UNIQUE (slug),
    CONSTRAINT fk_comm_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE community_members (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id  BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER', -- OWNER | ADMIN | MEMBER
    joined_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_community_member UNIQUE (community_id, user_id),
    CONSTRAINT fk_cm_comm FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_cm_user (user_id)
) ENGINE = InnoDB;

CREATE TABLE channels (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    name         VARCHAR(40) NOT NULL,
    topic        VARCHAR(200) NULL,
    position     INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_channel_name UNIQUE (community_id, name),
    CONSTRAINT fk_ch_comm FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE channel_messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    sender_id  BIGINT NOT NULL,
    content    VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cmsg_channel FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE,
    CONSTRAINT fk_cmsg_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_cmsg_channel (channel_id, id)
) ENGINE = InnoDB;
