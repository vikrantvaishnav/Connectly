-- Final test-artifact sweep (follow-up to V12).
--
-- V12 removed the seeded demo accounts; three one-off verification accounts
-- survived because they were created ad hoc, not by the seed script:
--  - cloudtest1790174539: "Cloud Tester" from the original cloud-DB smoke test,
--    whose lone post ("persistence works") would otherwise be the first thing
--    a new visitor sees in Explore
--  - precheck1790313905 / newuser1790314619: pre/post-purge register+login
--    verification accounts (their test post was already deleted via the API)
--
-- Deleted by exact username so no legitimate account can ever match.

DELETE FROM users WHERE username IN (
    'cloudtest1790174539',
    'precheck1790313905',
    'newuser1790314619'
);
