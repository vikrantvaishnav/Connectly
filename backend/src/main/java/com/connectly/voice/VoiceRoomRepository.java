package com.connectly.voice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceRoomRepository extends JpaRepository<VoiceRoom, Long> {
    List<VoiceRoom> findByStatusOrderByCreatedAtDesc(VoiceRoom.Status status);
}
