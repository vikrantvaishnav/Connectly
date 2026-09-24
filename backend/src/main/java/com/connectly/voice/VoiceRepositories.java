package com.connectly.voice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VoiceParticipantRepository extends JpaRepository<VoiceParticipant, Long> {
    Optional<VoiceParticipant> findByRoomIdAndUserId(Long roomId, Long userId);
    List<VoiceParticipant> findByRoomId(Long roomId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    /** Batch participant counts for the open-rooms list (one query instead of N). */
    @Query("select p.room.id, count(p) from VoiceParticipant p where p.room.id in :roomIds group by p.room.id")
    List<Object[]> countByRoomIdIn(@Param("roomIds") Collection<Long> roomIds);
}
