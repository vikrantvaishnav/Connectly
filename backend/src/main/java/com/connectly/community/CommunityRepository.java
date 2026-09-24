package com.connectly.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityRepository extends JpaRepository<Community, Long> {
    Optional<Community> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Community> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
