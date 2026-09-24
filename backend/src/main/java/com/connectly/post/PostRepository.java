package com.connectly.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** Authors are join-fetched: rendering a page costs 1 query, not 1+N. */
    @Query("""
            select p from Post p join fetch p.author
            where p.author.id = :authorId
            order by p.createdAt desc
            """)
    Page<Post> findByAuthorIdOrderByCreatedAtDesc(@Param("authorId") Long authorId, Pageable pageable);

    @Query("""
            select p from Post p join fetch p.author
            where p.author.id in :authorIds
              and (p.visibility = com.connectly.post.Post$Visibility.PUBLIC
                   or p.visibility = com.connectly.post.Post$Visibility.FOLLOWERS)
            order by p.createdAt desc
            """)
    Page<Post> feedFor(@Param("authorIds") List<Long> authorIds, Pageable pageable);

    @Query("""
            select p from Post p join fetch p.author
            where p.visibility = :visibility
            order by p.createdAt desc
            """)
    Page<Post> findByVisibilityOrderByCreatedAtDesc(@Param("visibility") Post.Visibility visibility, Pageable pageable);

    @Query("""
            select p from Post p join fetch p.author
            where p.visibility = com.connectly.post.Post$Visibility.PUBLIC
              and lower(p.content) like lower(concat('%', :term, '%'))
            order by p.createdAt desc
            """)
    List<Post> searchPublicByContent(@Param("term") String term, Pageable pageable);

    long countByAuthorId(Long authorId);
}
