package com.connectly.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    /** Batch profile load (one query instead of N per-page lookups). */
    List<UserProfile> findByUserIdIn(Collection<Long> userIds);
}
