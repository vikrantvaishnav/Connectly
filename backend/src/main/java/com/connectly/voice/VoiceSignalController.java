package com.connectly.voice;

import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Relays WebRTC signaling (SDP offers/answers, ICE candidates) between verified
 * room participants. The relay validates sender is in the room; payloads are
 * opaque to the server. Addressed delivery: `to` null = broadcast to room topic.
 */
@Controller
public class VoiceSignalController {

    private final VoiceParticipantRepository participants;
    private final SimpMessagingTemplate broker;

    public VoiceSignalController(VoiceParticipantRepository participants, SimpMessagingTemplate broker) {
        this.participants = participants;
        this.broker = broker;
    }

    public record SignalEnvelope(Long from, Long to, Object data) {}

    @MessageMapping("/voice/{roomId}/signal")
    public void signal(@DestinationVariable long roomId,
                       Principal principal,
                       @Payload SignalEnvelope envelope) {
        Long senderId = Long.valueOf(principal.getName());
        // only participants may relay signals on this room
        if (!participants.existsByRoomIdAndUserId(roomId, senderId)) {
            return; // silently drop — no existence probing
        }
        Long to = envelope.to();
        Map<String, Object> payload = Map.of("from", senderId, "to", to, "data", envelope.data());
        if (to == null) {
            broker.convertAndSend("/topic/voice/" + roomId + "/signal", (Object) payload);
        } else {
            // per-user queue keeps private signals truly private
            broker.convertAndSendToUser(String.valueOf(to), "/queue/voice/" + roomId + "/signal", (Object) payload);
        }
    }
}
