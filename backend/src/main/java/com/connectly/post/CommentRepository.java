package com.connectly.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Batch comment counts for a page of posts (one query instead of N). */
    @Query("""
           select c.post.id, count(c)
           from Comment c
           where c.post.id in :postIds
           group by c.post.id
           """)
    List<Object[]> countByPostIdIn(@Param("postIds") Collection<Long> postIds);

    @Query("""
            select c from Comment c
            join fetch c.author
            where c.post.id = :postId
            order by c.createdAt asc
            """)
    List<Comment> findByPostIdWithAuthor(@Param("postId") Long postId);

    long countByPostId(Long postId);
}
