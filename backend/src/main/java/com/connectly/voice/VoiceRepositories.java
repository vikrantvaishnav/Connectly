package com.connectly.voice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface VoiceParticipantRepository extends JpaRepository<VoiceParticipant, Long> {
    Optional<VoiceParticipant> findByRoomIdAndUserId(Long roomId, Long userId);
    List<VoiceParticipant> findByRoomId(Long roomId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
}
