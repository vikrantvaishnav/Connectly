package com.connectly.community;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership check used by the WebSocket layer to authorize channel topic
 * subscriptions (e.g. typing pings). Kept separate from CommunityService so the
 * STOMP config depends on one narrow, read-only query instead of the whole
 * community surface — and so there is no bean cycle back into chat.
 */
@Component
public class ChannelAccessChecker {

    private final ChannelRepository channels;
    private final CommunityMemberRepository members;

    public ChannelAccessChecker(ChannelRepository channels, CommunityMemberRepository members) {
        this.channels = channels;
        this.members = members;
    }

    /** A user may subscribe to a channel topic only if they belong to its community. */
    @Transactional(readOnly = true)
    public boolean isMemberOfChannel(Long userId, Long channelId) {
        if (userId == null || channelId == null) return false;
        return channels.findById(channelId)
                .map(ch -> members.existsByCommunityIdAndUserId(ch.getCommunityId(), userId))
                .orElse(false);
    }
}
