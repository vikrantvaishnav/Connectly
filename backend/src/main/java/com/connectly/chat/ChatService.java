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
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final ChatPusher pusher;

    public ChatService(ConversationRepository conversations, MessageRepository messages,
                       ConversationStateRepository states, UserRepository users,
                       UserProfileRepository profiles, ChatPusher pusher) {
        this.conversations = conversations;
        this.messages = messages;
        this.states = states;
        this.users = users;
        this.profiles = profiles;
        this.pusher = pusher;
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
        return convs.stream()
                .map(c -> toSummary(c, me, unread.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    private ConversationSummary toSummary(Conversation c, User me, long unread) {
        User other = c.otherOf(me);
        UserProfile p = profiles.findByUserId(other.getId()).orElse(null);
        return new ConversationSummary(
                c.getId(), other.getId(), other.getUsername(),
                p == null ? null : p.getFirstName(), p == null ? null : p.getLastName(),
                c.getLastMessage(), c.getLastMessageAt(), unread);
    }

    public record ConversationSummary(Long id, Long otherUserId, String otherUsername,
                                      String otherFirstName, String otherLastName,
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
        return messages.findByConversationIdOrderByIdDesc(conv.getId(), pageable).stream()
                .map(m -> toView(m, me.getId()))
                .toList();
    }

    public record MessageView(Long id, Long senderId, String senderUsername,
                              String content, Instant createdAt, boolean mine) {}

    private MessageView toView(Message m, Long meId) {
        return new MessageView(m.getId(), m.getSender().getId(), m.getSender().getUsername(),
                m.getContent(), m.getCreatedAt(), m.getSender().getId().equals(meId));
    }

    @Transactional
    public MessageView send(User me, Long conversationId, String content) {
        Conversation conv = requireParticipant(me, conversationId);
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
        return toView(m, me.getId());
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
