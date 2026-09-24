package com.connectly.chat;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {

    Optional<ConversationState> findByConversationIdAndUserId(Long conversationId, Long userId);
}
