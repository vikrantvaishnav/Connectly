package com.connectly.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /** Batch like counts for a page of posts (one query instead of N). */
    @Query("""
           select l.post.id, count(l)
           from PostLike l
           where l.post.id in :postIds
           group by l.post.id
           """)
    List<Object[]> countByPostIdIn(@Param("postIds") Collection<Long> postIds);

    /** Which of the given posts the viewer has liked (one query instead of N). */
    @Query("""
           select l.post.id
           from PostLike l
           where l.user.id = :userId and l.post.id in :postIds
           """)
    List<Long> findPostIdsLikedBy(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
