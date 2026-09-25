-- Removes the vcheck* accounts created while verifying that the new
-- register-endpoint auto-login contract went live on production.
-- (The old response shape had no tokens; the new one returns a session.)

DELETE FROM users WHERE username LIKE 'vcheck%';
