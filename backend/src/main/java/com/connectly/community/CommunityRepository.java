package com.connectly.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommunityRepository extends JpaRepository<Community, Long> {
    Optional<Community> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Community> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Owner is join-fetched so listing communities costs 1 query, not 1+N. */
    @Query("select c from Community c join fetch c.owner order by c.createdAt desc")
    List<Community> findAllWithOwner(Pageable pageable);
}
