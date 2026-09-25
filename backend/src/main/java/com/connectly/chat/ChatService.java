package com.connectly.chat;

import com.connectly.common.error.ApiException;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final int MAX_CONTENT = 4000;
    private static final Pageable INBOX_PAGE = PageRequest.of(0, 100);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationStateRepository states;
    private final MessageReactionRepository reactions;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final ChatPusher pusher;
    private final com.connectly.social.SafetyService safety;

    public ChatService(ConversationRepository conversations, MessageRepository messages,
                       ConversationStateRepository states, MessageReactionRepository reactions,
                       UserRepository users, UserProfileRepository profiles, ChatPusher pusher,
                       com.connectly.social.SafetyService safety) {
        this.conversations = conversations;
        this.messages = messages;
        this.states = states;
        this.reactions = reactions;
        this.users = users;
        this.profiles = profiles;
        this.pusher = pusher;
        this.safety = safety;
    }

    // ---- inbox ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConversationSummary> inbox(User me) {
        List<Conversation> convs = conversations.findConversationsOf(me.getId(), INBOX_PAGE);
        if (convs.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Conversation c : convs) {
            ids.add(c.getId());
        }
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : messages.countUnread(me.getId(), ids)) {
            unread.put((Long) row[0], (Long) row[1]);
        }
        // Batch-resolve the other participants and their profiles (2 queries, not 2N).
        List<Long> otherIds = convs.stream()
                .map(c -> c.otherOf(me).getId())
                .distinct()
                .toList();
        Map<Long, User> otherById = new HashMap<>();
        for (User u : users.findAllById(otherIds)) {
            otherById.put(u.getId(), u);
        }
        Map<Long, UserProfile> profileById = new HashMap<>();
        for (UserProfile p : profiles.findByUserIdIn(otherIds)) {
            profileById.put(p.getUserId(), p);
        }
        return convs.stream()
                .map(c -> {
                    User other = otherById.getOrDefault(c.otherOf(me).getId(), c.otherOf(me));
                    UserProfile p = profileById.get(other.getId());
                    return new ConversationSummary(
                            c.getId(), other.getId(), other.getUsername(),
                            p == null ? null : p.getFirstName(), p == null ? null : p.getLastName(),
                            p == null ? null : p.getProfileImage(),
                            c.getLastMessage(), c.getLastMessageAt(),
                            unread.getOrDefault(c.getId(), 0L));
                })
                .toList();
    }

    private ConversationSummary toSummary(Conversation c, User me, long unread) {
        User other = c.otherOf(me);
        UserProfile p = profiles.findByUserId(other.getId()).orElse(null);
        return new ConversationSummary(
                c.getId(), other.getId(), other.getUsername(),
                p == null ? null : p.getFirstName(), p == null ? null : p.getLastName(),
                p == null ? null : p.getProfileImage(),
                c.getLastMessage(), c.getLastMessageAt(), unread);
    }

    public record ConversationSummary(Long id, Long otherUserId, String otherUsername,
                                      String otherFirstName, String otherLastName, String otherProfileImage,
                                      String lastMessage, Instant lastMessageAt, long unread) {}

    // ---- conversation lifecycle -------------------------------------------

    /** Returns the 1:1 conversation with the other user, creating it if needed. */
    @Transactional
    public ConversationSummary openWith(User me, Long otherUserId) {
        if (otherUserId == null || otherUserId.equals(me.getId())) {
            throw ApiException.badRequest("Cannot open a chat with yourself");
        }
        if (!users.existsById(otherUserId)) {
            throw ApiException.notFound("User not found");
        }
        // Blocked pairs cannot open new conversations (and blocks delete old ones).
        safety.requireNotBlocked(me.getId(), otherUserId);

        long a = Math.min(me.getId(), otherUserId);
        long b = Math.max(me.getId(), otherUserId);
        Conversation conv = conversations.findByUserAIdAndUserBId(a, b)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setUserA(users.getReferenceById(a));
                    c.setUserB(users.getReferenceById(b));
                    return conversations.save(c);
                });
        return toSummary(conv, me, 0);
    }

    // ---- messages ----------------------------------------------------------

    /**
     * Object-level authorization: only the two participants may read a conversation.
     * Strangers get 404 so conversation existence is never leaked.
     */
    @Transactional(readOnly = true)
    public List<MessageView> messages(User me, Long conversationId, int page, int size) {
        Conversation conv = requireParticipant(me, conversationId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        // senders are join-fetched (see repository) — no per-message author query
        List<Message> page1 = messages.findByConversationIdOrderByIdDesc(conv.getId(), pageable);
        Map<Long, List<ReactionView>> byMessage = reactionsFor(page1, me.getId());
        return page1.stream()
                .map(m -> toView(m, me.getId(), byMessage.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    public record MessageView(Long id, Long senderId, String senderUsername,
                              String content, Instant createdAt, boolean mine,
                              List<ReactionView> reactions) {}

    /** One emoji tally on a message; {@code mine} drives the highlighted chip. */
    public record ReactionView(String emoji, long count, boolean mine) {}

    private MessageView toView(Message m, Long meId, List<ReactionView> reactions) {
        return new MessageView(m.getId(), m.getSender().getId(), m.getSender().getUsername(),
                m.getContent(), m.getCreatedAt(), m.getSender().getId().equals(meId), reactions);
    }

    /** Batched reaction tally for a page of messages (one query). */
    private Map<Long, List<ReactionView>> reactionsFor(List<Message> page, Long meId) {
        if (page.isEmpty()) return Map.of();
        List<Long> ids = page.stream().map(Message::getId).toList();
        // emoji -> count / do I have it, per message
        Map<Long, Map<String, long[]>> tallies = new HashMap<>();
        Map<Long, Set<String>> mine = new HashMap<>();
        for (Object[] row : reactions.findAllFor(ids)) {
            Long messageId = (Long) row[0];
            Long userId = (Long) row[1];
            String emoji = (String) row[2];
            tallies.computeIfAbsent(messageId, k -> new java.util.LinkedHashMap<>())
                    .computeIfAbsent(emoji, k -> new long[1])[0]++;
            if (meId != null && meId.equals(userId)) {
                mine.computeIfAbsent(messageId, k -> new HashSet<>()).add(emoji);
            }
        }
        Map<Long, List<ReactionView>> out = new HashMap<>();
        for (Map.Entry<Long, Map<String, long[]>> e : tallies.entrySet()) {
            Set<String> myEmojis = mine.getOrDefault(e.getKey(), Set.of());
            out.put(e.getKey(), e.getValue().entrySet().stream()
                    .map(en -> new ReactionView(en.getKey(), en.getValue()[0], myEmojis.contains(en.getKey())))
                    .toList());
        }
        return out;
    }

    @Transactional
    public MessageView send(User me, Long conversationId, String content) {
        Conversation conv = requireParticipant(me, conversationId);
        // Defense in depth: a block deletes the shared conversation, but if a
        // client held the id, sending still fails closed.
        safety.requireNotBlocked(me.getId(), conv.otherOf(me).getId());
        String body = content == null ? "" : content.trim();
        if (body.isEmpty()) {
            throw ApiException.badRequest("Message must not be empty");
        }
        if (body.length() > MAX_CONTENT) {
            throw ApiException.badRequest("Message too long (max " + MAX_CONTENT + " characters)");
        }

        Message m = new Message();
        m.setConversation(conv);
        m.setSender(me);
        m.setContent(body);
        m = messages.save(m);

        conv.setLastMessage(summarize(body));
        conv.setLastMessageAt(m.getCreatedAt());
        conversations.save(conv);

        // Sender has by definition read up to their own last message.
        markReadInternal(me, conv, m.getId());

        User other = conv.otherOf(me);
        pusher.pushNewMessage(other.getId(), conv.getId(), m.getId(), me.getId(), me.getUsername());
        return toView(m, me.getId(), List.of());
    }

    /** Ephemeral typing notice — not stored, just relayed to the other participant. */
    @Transactional(readOnly = true)
    public void typing(User me, Long conversationId) {
        Conversation conv = requireParticipant(me, conversationId);
        User other = conv.otherOf(me);
        pusher.pushTyping(other.getId(), conv.getId(), me.getId(), me.getUsername());
    }

    /** Toggles one emoji from the caller on a message; returns the message's new tallies. */
    @Transactional
    public List<ReactionView> toggleReaction(User me, Long messageId, String rawEmoji) {
        String emoji = rawEmoji == null ? "" : rawEmoji.strip();
        if (emoji.isEmpty() || emoji.length() > 16) {
            throw ApiException.badRequest("Pick a single emoji");
        }
        Message m = messages.findById(messageId)
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        Conversation conv = requireParticipant(me, m.getConversation().getId());

        reactions.findByMessageIdAndUserIdAndEmoji(messageId, me.getId(), emoji).ifPresentOrElse(
                reactions::delete,
                () -> {
                    MessageReaction r = new MessageReaction();
                    r.setMessage(m);
                    r.setUser(me);
                    r.setEmoji(emoji);
                    reactions.save(r);
                });

        pusher.pushReaction(conv.otherOf(me).getId(), conv.getId(), messageId);
        return reactionsFor(List.of(m), me.getId()).getOrDefault(messageId, List.of());
    }

    private static String summarize(String body) {
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 497) + "...";
    }

    // ---- read state ---------------------------------------------------------

    @Transactional
    public void markRead(User me, Long conversationId, Long upToMessageId) {
        Conversation conv = requireParticipant(me, conversationId);
        Long upTo = (upToMessageId != null) ? upToMessageId : latestId(conv);
        markReadInternal(me, conv, upTo);
    }

    private void markReadInternal(User me, Conversation conv, Long upTo) {
        ConversationState st = states.findByConversationIdAndUserId(conv.getId(), me.getId())
                .orElseGet(() -> {
                    ConversationState s = new ConversationState();
                    s.setConversationId(conv.getId());
                    s.setUserId(me.getId());
                    return s;
                });
        if (upTo != null && upTo > st.getLastReadMessageId()) {
            st.setLastReadMessageId(upTo);
            states.save(st);
        }
    }

    private Long latestId(Conversation conv) {
        List<Message> last = messages.findByConversationIdOrderByIdDesc(conv.getId(), PageRequest.of(0, 1));
        return last.isEmpty() ? 0L : last.get(0).getId();
    }

    // ---- authorization helper -----------------------------------------------

    private Conversation requireParticipant(User me, Long conversationId) {
        Conversation conv = conversations.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!conv.involves(me)) {
            // 404, not 403 — never leak the existence of other people's conversations.
            throw ApiException.notFound("Conversation not found");
        }
        return conv;
    }
}
