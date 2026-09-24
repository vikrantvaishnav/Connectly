-- Phase 8: performance indexes for the batched read paths.
-- posts feed/explore: filter on visibility, sort by created_at
ALTER TABLE posts
    ADD INDEX idx_posts_vis_created (visibility, created_at DESC);

-- unread counting joins messages on (conversation_id, sender_id, id)
ALTER TABLE messages
    ADD INDEX idx_msg_conv_sender (conversation_id, sender_id, id);

-- user_profiles is joined by user_id in every batched page render
ALTER TABLE user_profiles
    ADD INDEX idx_user_profiles_user (user_id);
