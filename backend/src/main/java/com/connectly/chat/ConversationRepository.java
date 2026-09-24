package com.connectly.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 1:1 conversations are stored with the smaller user id in user_a_id so the
     * pair unique-constraint prevents duplicates regardless of who starts the chat.
     */
    Optional<Conversation> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("""
           select c from Conversation c
           where c.userA.id = :userId or c.userB.id = :userId
           order by coalesce(c.lastMessageAt, c.createdAt) desc
           """)
    List<Conversation> findConversationsOf(@Param("userId") Long userId, Pageable pageable);
}
