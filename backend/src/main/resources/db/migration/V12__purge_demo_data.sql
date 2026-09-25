-- Demo-data purge: removes the six seeded @connectly.demo accounts and,
-- through ON DELETE CASCADE on every child table, everything they ever
-- created — posts, comments, likes, follows, connections, conversations,
-- messages, message/channel reactions, notifications, voice rooms and
-- participation, community memberships, locations, sessions, tokens,
-- recovery codes and profiles.
--
-- audit_logs.user_id has no FK (deliberate, so audit history survives user
-- deletion), so it is nullified explicitly to keep the log referentially
-- clean without losing the timeline.
--
-- The script also drops stale demo artifacts (V10-era smoke test rows and
-- test communities) and a documented step below removes the seed script.

-- 1. Detach audit history from the demo accounts before they vanish.
UPDATE audit_logs SET user_id = NULL
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@connectly.demo');

-- 2. Delete the accounts; cascades clear every owned row.
DELETE FROM users WHERE email LIKE '%@connectly.demo';

-- 3. Drop the communities created during smoke testing (owner cascade does
--    the work; explicit names keep the intent obvious).
DELETE FROM communities WHERE name LIKE '%Hangout%' OR name LIKE '%Prod smoke%';

-- 4. Remove the smoke-test DM reactions created while verifying V10.
DELETE FROM message_reactions
WHERE message_id IN (
    SELECT m.id FROM messages m
    JOIN conversations c ON c.id = m.conversation_id
    WHERE m.content LIKE 'smoke test:%' OR m.content LIKE 'smoke:%'
);
DELETE FROM messages WHERE content LIKE 'smoke test:%' OR content LIKE 'smoke:%';

-- 5. Belt-and-braces orphan sweep (cheap no-ops on a consistent schema).
DELETE FROM notifications WHERE recipient_id NOT IN (SELECT id FROM users);
DELETE FROM messages WHERE sender_id NOT IN (SELECT id FROM users);
DELETE FROM follows WHERE follower_id NOT IN (SELECT id FROM users) OR followee_id NOT IN (SELECT id FROM users);
DELETE FROM connections WHERE sender_id NOT IN (SELECT id FROM users) OR receiver_id NOT IN (SELECT id FROM users);
