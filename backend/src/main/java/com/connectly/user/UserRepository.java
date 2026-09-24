package com.connectly.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select u from User u
            left join UserProfile p on p.userId = u.id
            where lower(u.username) like lower(concat('%', :term, '%'))
               or lower(coalesce(p.firstName, '')) like lower(concat('%', :term, '%'))
               or lower(coalesce(p.lastName, '')) like lower(concat('%', :term, '%'))
               or lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''))) like lower(concat('%', :term, '%'))
            order by u.username asc
            """)
    List<User> searchUsers(@Param("term") String term, Pageable pageable);
}
