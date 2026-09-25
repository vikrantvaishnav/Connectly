package com.connectly.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId);

    /** Purges notifications between two users (block teardown, account deletion). */
    @Modifying
    @Query("delete from Notification n where (n.recipient.id = :a and n.actor.id = :b) or (n.recipient.id = :b and n.actor.id = :a)")
    void deleteAllForUser(@Param("a") Long a, @Param("b") Long b);

    /** Purges everything a user would ever receive — used by account deletion. */
    @Modifying
    @Query("delete from Notification n where n.recipient.id = :userId or n.actor.id = :userId")
    void deleteAllInvolving(@Param("userId") Long userId);
}
