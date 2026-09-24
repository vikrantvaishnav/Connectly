package com.connectly.post;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {

    Optional<SavedPost> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    List<SavedPost> findByUserIdOrderByCreatedAtDesc(Long userId);
}
