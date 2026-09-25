-- Email OTP codes for registration activation (one row per pending email).
CREATE TABLE email_otp_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_email_otp_email UNIQUE (email)
);

CREATE INDEX idx_email_otp_created ON email_otp_codes (created_at);
