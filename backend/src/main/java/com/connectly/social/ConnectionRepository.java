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

    long countByReceiverIdAndStatus(Long receiverId, Connection.Status status);

    long countBySenderIdAndStatus(Long senderId, Connection.Status status);

    /**
     * Every connection row (either direction) between {@code me} and any of {@code ids}.
     * Powers batched relationship badges on discovery cards without an N+1.
     */
    @Query("""
            select c from Connection c
            join fetch c.sender join fetch c.receiver
            where (c.sender.id = :me and c.receiver.id in :ids)
               or (c.receiver.id = :me and c.sender.id in :ids)
            """)
    List<Connection> findAllBetween(@Param("me") Long me, @Param("ids") java.util.Collection<Long> ids);
}
