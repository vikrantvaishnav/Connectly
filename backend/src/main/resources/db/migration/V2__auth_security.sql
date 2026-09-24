-- Phase 2 security: sessions, email/verification tokens, audit logs,
-- TOTP recovery codes, and hardened user columns.

ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP NULL;

CREATE TABLE auth_sessions (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    device            VARCHAR(120) NULL,
    ip                VARCHAR(45)  NULL,
    user_agent        VARCHAR(300) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        TIMESTAMP    NOT NULL,
    revoked_at        TIMESTAMP    NULL,
    CONSTRAINT uq_auth_sessions_token UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_sessions_user ON auth_sessions (user_id);

CREATE TABLE auth_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP    NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_tokens_type CHECK (type IN ('VERIFY_EMAIL', 'PASSWORD_RESET'))
);

CREATE INDEX idx_auth_tokens_user_type ON auth_tokens (user_id, type);

CREATE TABLE recovery_codes (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT       NOT NULL,
    code_hash VARCHAR(64)  NOT NULL,
    used_at   TIMESTAMP    NULL,
    CONSTRAINT uq_recovery_codes_hash UNIQUE (code_hash),
    CONSTRAINT fk_recovery_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE audit_logs (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NULL,
    event      VARCHAR(40)  NOT NULL,
    detail     VARCHAR(300) NULL,
    ip         VARCHAR(45)  NULL,
    user_agent VARCHAR(300) NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_event ON audit_logs (event);
CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);
