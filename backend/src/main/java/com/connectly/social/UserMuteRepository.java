package com.connectly.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserMuteRepository extends JpaRepository<UserMute, Long> {

    Optional<UserMute> findByMuterIdAndMutedId(Long muterId, Long mutedId);

    boolean existsByMuterIdAndMutedId(Long muterId, Long mutedId);

    List<UserMute> findByMuterIdOrderByCreatedAtDesc(Long muterId);

    /** Which of these ids does the viewer mute? One query for a whole page. */
    @Query("select m.muted.id from UserMute m where m.muter.id = :me and m.muted.id in :ids")
    List<Long> findMutedIdsAmong(@Param("me") Long me, @Param("ids") Collection<Long> ids);
}
