package com.connectly.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    /** All reactions on a page of messages — one query, aggregated in memory. */
    @Query("select r.message.id, r.user.id, r.emoji from MessageReaction r where r.message.id in :ids")
    List<Object[]> findAllFor(@Param("ids") Collection<Long> ids);
}
