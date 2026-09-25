package com.connectly.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    /** Union check for the safety gate: did either side block the other? */
    @Query("""
           select count(b) > 0 from UserBlock b
           where (b.blocker.id = :a and b.blocked.id = :b)
              or (b.blocker.id = :b and b.blocked.id = :a)
           """)
    boolean existsEitherDirection(@Param("a") Long a, @Param("b") Long b);

    /**
     * Every user id that {@code userId} has blocked or is blocked by, in one query.
     * Powers the batched visibility filter on feed/explore/nearby/chat lists.
     */
    @Query("""
           select b.blocked.id from UserBlock b where b.blocker.id = :userId
           union
           select b.blocker.id from UserBlock b where b.blocked.id = :userId
           """)
    List<Long> blockedIdsInvolving(@Param("userId") Long userId);

    /** Batched variant for rendering a page: which of these ids involve a block with me? */
    @Query("""
           select case when b.blocker.id = :me then b.blocked.id else b.blocker.id end
           from UserBlock b
           where (b.blocker.id = :me and b.blocked.id in :ids)
              or (b.blocked.id = :me and b.blocker.id in :ids)
           """)
    List<Long> findBlockedOthersAmong(@Param("me") Long me, @Param("ids") Collection<Long> ids);
}
