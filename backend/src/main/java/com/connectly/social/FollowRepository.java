package com.connectly.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    List<Follow> findByFollowerId(Long followerId);
    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    long countByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);
}
