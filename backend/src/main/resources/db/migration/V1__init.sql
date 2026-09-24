-- Connectly initial schema.
-- Written in portable SQL (no MySQL-specific clauses) so it also runs on
-- H2 in MySQL mode for tests. Phase 2+ migrations add social/chat tables.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login    TIMESTAMP    NULL,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'DELETED'))
);

CREATE INDEX idx_users_status ON users (status);

CREATE TABLE user_profiles (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    first_name    VARCHAR(80)  NULL,
    last_name     VARCHAR(80)  NULL,
    bio           VARCHAR(500) NULL,
    profile_image VARCHAR(500) NULL,
    cover_image   VARCHAR(500) NULL,
    date_of_birth DATE         NULL,
    profession    VARCHAR(120) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_locations (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    latitude     DECIMAL(9, 6) NULL,
    longitude    DECIMAL(9, 6) NULL,
    discoverable BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_locations_user UNIQUE (user_id),
    CONSTRAINT fk_user_locations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
