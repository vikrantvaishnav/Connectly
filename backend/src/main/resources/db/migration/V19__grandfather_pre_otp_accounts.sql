-- Grandfather accounts created before the OTP gate: they registered under the
-- old auto-login policy, so they must not be locked out by the new rule.
-- Every account created AFTER this deploy goes through email OTP activation.
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
