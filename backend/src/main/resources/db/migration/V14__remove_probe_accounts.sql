-- Point-in-time cleanup of the ad-hoc probe accounts created while waiting
-- for the V13 deploy (register+login probes used to confirm the purge).
-- Verified afterwards without creating further accounts: these usernames
-- simply fail to log in.

DELETE FROM users WHERE username LIKE 'probe%';
