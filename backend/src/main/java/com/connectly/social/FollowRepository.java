package com.connectly.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    List<Follow> findByFollowerId(Long followerId);
    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    /** Follow requests waiting on this account's approval (for the requests UI). */
    List<Follow> findByFolloweeIdAndStatusOrderByCreatedAtDesc(Long followeeId, Follow.Status status);

    /** All requests I have sent that are still pending. */
    List<Follow> findByFollowerIdAndStatus(Long followerId, Follow.Status status);

    /** ACTIVE follows only — pending requests never count as followers/following. */
    long countByFollowerIdAndStatus(Long followerId, Follow.Status status);

    long countByFolloweeIdAndStatus(Long followeeId, Follow.Status status);

    /**
     * Of {@code ids}, which users follow {@code followeeId}? (batched "follows you" badge)
     * One query instead of one exists() per card.
     */
    @Query("select f.follower.id from Follow f where f.followee.id = :followeeId and f.follower.id in :ids")
    List<Long> findFollowerIdsAmong(@Param("followeeId") Long followeeId, @Param("ids") Collection<Long> ids);
}
