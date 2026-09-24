package com.connectly.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    Optional<Connection> findBySenderIdAndReceiverId(Long senderId, Long receiverId);

    /** Any directed row between the two users (either direction). */
    @Query("""
            select c from Connection c
            where (c.sender.id = :a and c.receiver.id = :b)
               or (c.sender.id = :b and c.receiver.id = :a)
            """)
    Optional<Connection> findBetween(@Param("a") Long a, @Param("b") Long b);

    List<Connection> findByReceiverIdAndStatusOrderByCreatedAtDesc(Long receiverId, Connection.Status status);

    List<Connection> findBySenderIdAndStatusOrderByCreatedAtDesc(Long senderId, Connection.Status status);
}
