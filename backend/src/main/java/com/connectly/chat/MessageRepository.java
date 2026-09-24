package com.connectly.chat;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId, Pageable pageable);

    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    /**
     * Unread counts per conversation for the inbox badge (batched, no N+1).
     * LEFT join: a conversation the user never opened has no state row yet —
     * every other participant's message counts as unread (coalesce to 0).
     */
    @Query("""
           select m.conversation.id, count(m)
           from Message m
           left join ConversationState s on s.conversationId = m.conversation.id
             and s.userId = :userId
           where m.conversation.id in :conversationIds
             and m.sender.id <> :userId
             and m.id > coalesce(s.lastReadMessageId, 0)
           group by m.conversation.id
           """)
    List<Object[]> countUnread(@Param("userId") Long userId, @Param("conversationIds") Collection<Long> conversationIds);
}
