package com.connectly.community;

import com.connectly.common.error.ApiException;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

    private final CommunityRepository communities;
    private final CommunityMemberRepository members;
    private final ChannelRepository channels;
    private final ChannelMessageRepository channelMessages;
    private final com.connectly.user.UserRepository users;
    private final UserProfileRepository profiles;

    public CommunityService(CommunityRepository communities, CommunityMemberRepository members,
                            ChannelRepository channels, ChannelMessageRepository channelMessages,
                            com.connectly.user.UserRepository users, UserProfileRepository profiles) {
        this.communities = communities;
        this.members = members;
        this.channels = channels;
        this.channelMessages = channelMessages;
        this.users = users;
        this.profiles = profiles;
    }

    // ---- DTOs ----

    public record CommunityView(long id, String name, String slug, String description, String icon,
                                String ownerUsername, long memberCount, boolean isMember,
                                String myRole, Instant createdAt) {}

    public record ChannelView(long id, String name, String topic) {}

    public record MemberView(long userId, String username, String name, String role) {}

    public record ChannelMessageView(long id, long senderId, String senderUsername, String senderName,
                                     String content, Instant createdAt, boolean mine) {}

    // ---- helpers ----

    private String nameOf(User u) {
        UserProfile p = profiles.findByUserId(u.getId()).orElse(null);
        String n = p == null ? null
                : java.util.stream.Stream.of(p.getFirstName(), p.getLastName())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
        return (n == null || n.isBlank()) ? u.getUsername() : n;
    }

    private CommunityView toView(Community c, User me, long memberCount, String myRole) {
        return new CommunityView(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getIcon(),
                c.getOwner().getUsername(), memberCount, myRole != null, myRole, c.getCreatedAt());
    }

    private Community requireCommunity(long id) {
        return communities.findById(id).orElseThrow(() -> ApiException.notFound("Community not found"));
    }

    /** 404 unless the user is a member — membership is the access control. */
    private CommunityMember requireMember(Community c, User me) {
        return members.findByCommunityIdAndUserId(c.getId(), me.getId())
                .orElseThrow(() -> ApiException.notFound("Community not found"));
    }

    private void requireAtLeast(CommunityMember m, CommunityMember.Role min) {
        int rank = switch (m.getRole()) { case MEMBER -> 0; case ADMIN -> 1; case OWNER -> 2; };
        int need = switch (min) { case MEMBER -> 0; case ADMIN -> 1; case OWNER -> 2; };
        if (rank < need) throw ApiException.forbidden("Insufficient role");
    }

    private String slugify(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isBlank()) base = "community";
        String slug = base;
        int i = 2;
        while (communities.existsBySlug(slug)) {
            slug = base + "-" + (i++);
            if (i > 50) { slug = base + "-" + java.util.UUID.randomUUID().toString().substring(0, 6); break; }
        }
        return slug;
    }

    // ---- community lifecycle ----

    @Transactional
    public CommunityView create(User me, String name, String description, String icon) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() < 3 || trimmed.length() > 60) {
            throw ApiException.badRequest("Community name must be 3-60 characters");
        }
        Community c = new Community();
        c.setName(trimmed);
        c.setSlug(slugify(trimmed));
        c.setDescription(description == null ? null : description.strip());
        c.setIcon(icon == null || icon.isBlank() ? "💬" : icon.strip());
        c.setOwner(me);
        c = communities.save(c);

        CommunityMember owner = new CommunityMember();
        owner.setCommunity(c);
        owner.setUser(me);
        owner.setRole(CommunityMember.Role.OWNER);
        members.save(owner);

        // Default channels, Discord-style.
        createChannelInternal(c, "general", "General chat", 0);
        createChannelInternal(c, "announcements", "Important updates", 1);

        return toView(c, me, 1, "OWNER");
    }

    @Transactional(readOnly = true)
    public List<CommunityView> browse(User me) {
        Map<Long, String> myRoles = new HashMap<>();
        for (CommunityMember m : members.findAllMemberships(me.getId())) {
            myRoles.put(m.getCommunity().getId(), m.getRole().name());
        }
        return communities.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).stream()
                .map(c -> toView(c, me, members.countByCommunityId(c.getId()), myRoles.get(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityView get(User me, long id) {
        Community c = requireCommunity(id);
        return toView(c, me, members.countByCommunityId(c.getId()), myRoleOr(c, me, null));
    }

    private String myRoleOr(Community c, User me, String fallback) {
        return members.findByCommunityIdAndUserId(c.getId(), me.getId())
                .map(m -> m.getRole().name()).orElse(fallback);
    }

    @Transactional
    public CommunityView join(User me, long id) {
        Community c = requireCommunity(id);
        if (!members.existsByCommunityIdAndUserId(id, me.getId())) {
            CommunityMember m = new CommunityMember();
            m.setCommunity(c);
            m.setUser(me);
            m.setRole(CommunityMember.Role.MEMBER);
            members.save(m);
        }
        return toView(c, me, members.countByCommunityId(id), "MEMBER");
    }

    @Transactional
    public void leave(User me, long id) {
        Community c = requireCommunity(id);
        CommunityMember m = requireMember(c, me);
        if (m.getRole() == CommunityMember.Role.OWNER) {
            throw ApiException.badRequest("Owners cannot leave their own community");
        }
        members.delete(m);
    }

    // ---- channels ----

    private Channel createChannelInternal(Community c, String name, String topic, int position) {
        Channel ch = new Channel();
        ch.setCommunityId(c.getId());
        ch.setName(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-"));
        ch.setTopic(topic);
        ch.setPosition(position);
        return channels.save(ch);
    }

    @Transactional
    public ChannelView createChannel(User me, long communityId, String name, String topic) {
        Community c = requireCommunity(communityId);
        CommunityMember m = requireMember(c, me);
        requireAtLeast(m, CommunityMember.Role.ADMIN);
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() < 2 || trimmed.length() > 40) {
            throw ApiException.badRequest("Channel name must be 2-40 characters");
        }
        Channel ch = createChannelInternal(c, trimmed, topic,
                channels.findByCommunityIdOrderByPositionAscIdAsc(communityId).size());
        return new ChannelView(ch.getId(), ch.getName(), ch.getTopic());
    }

    @Transactional(readOnly = true)
    public List<ChannelView> channels(User me, long communityId) {
        Community c = requireCommunity(communityId);
        requireMember(c, me);
        return channels.findByCommunityIdOrderByPositionAscIdAsc(communityId).stream()
                .map(ch -> new ChannelView(ch.getId(), ch.getName(), ch.getTopic()))
                .toList();
    }

    // ---- members ----

    @Transactional(readOnly = true)
    public List<MemberView> members(User me, long communityId) {
        Community c = requireCommunity(communityId);
        requireMember(c, me);
        return members.findAll().stream()
                .filter(m -> m.getCommunity().getId().equals(communityId))
                .map(m -> new MemberView(m.getUser().getId(), m.getUser().getUsername(),
                        nameOf(m.getUser()), m.getRole().name()))
                .toList();
    }

    // ---- channel messages ----

    @Transactional(readOnly = true)
    public List<ChannelMessageView> messages(User me, long channelId, int page, int size) {
        Channel ch = channels.findById(channelId)
                .orElseThrow(() -> ApiException.notFound("Channel not found"));
        Community c = requireCommunity(ch.getCommunityId());
        requireMember(c, me);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return channelMessages.findByChannelIdOrderByIdDesc(ch.getId(), pageable).stream()
                .map(msg -> new ChannelMessageView(msg.getId(), msg.getSender().getId(),
                        msg.getSender().getUsername(), nameOf(msg.getSender()), msg.getContent(),
                        msg.getCreatedAt(), msg.getSender().getId().equals(me.getId())))
                .toList();
    }

    @Transactional
    public ChannelMessageView sendMessage(User me, long channelId, String content) {
        String body = content == null ? "" : content.strip();
        if (body.isEmpty()) throw ApiException.badRequest("Message must not be empty");
        if (body.length() > 4000) throw ApiException.badRequest("Message too long (max 4000)");
        Channel ch = channels.findById(channelId)
                .orElseThrow(() -> ApiException.notFound("Channel not found"));
        Community c = requireCommunity(ch.getCommunityId());
        requireMember(c, me);

        ChannelMessage msg = new ChannelMessage();
        msg.setChannel(ch);
        msg.setSender(me);
        msg.setContent(body);
        msg = channelMessages.save(msg);
        return new ChannelMessageView(msg.getId(), me.getId(), me.getUsername(), nameOf(me),
                msg.getContent(), msg.getCreatedAt(), true);
    }
}
