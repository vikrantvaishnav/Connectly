package com.connectly.voice;

import com.connectly.security.RateLimiter;
import com.connectly.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {

    private final VoiceService voice;
    private final RateLimiter rateLimiter;

    public VoiceController(VoiceService voice, RateLimiter rateLimiter) {
        this.voice = voice;
        this.rateLimiter = rateLimiter;
    }

    public record CreateRoomRequest(@NotBlank @Size(min = 2, max = 60) String name, Long communityId) {}
    public record MuteRequest(boolean muted) {}

    private void limit(String key, int n, int window) {
        if (!rateLimiter.allow(key, n, window)) {
            throw com.connectly.common.error.ApiException.tooManyRequests("Too many requests, slow down");
        }
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public VoiceService.RoomView create(@AuthenticationPrincipal User me, @RequestBody CreateRoomRequest req) {
        limit("voice-create:" + me.getUsername(), 10, 600);
        return voice.create(me, req.name(), req.communityId());
    }

    @GetMapping("/rooms")
    public List<VoiceService.RoomView> openRooms() {
        return voice.listOpen();
    }

    @PostMapping("/rooms/{id}/close")
    public VoiceService.RoomView close(@AuthenticationPrincipal User me, @PathVariable long id) {
        return voice.close(me, id);
    }

    @PostMapping("/rooms/{id}/join")
    public List<VoiceService.ParticipantView> join(@AuthenticationPrincipal User me, @PathVariable long id) {
        limit("voice-join:" + me.getUsername(), 30, 60);
        return voice.join(me, id);
    }

    @PostMapping("/rooms/{id}/leave")
    public Map<String, Object> leave(@AuthenticationPrincipal User me, @PathVariable long id) {
        voice.leave(me, id);
        return Map.of("ok", true);
    }

    @PostMapping("/rooms/{id}/mute")
    public List<VoiceService.ParticipantView> mute(@AuthenticationPrincipal User me,
                                                   @PathVariable long id,
                                                   @RequestBody MuteRequest req) {
        return voice.setMuted(me, id, req.muted());
    }

    @GetMapping("/rooms/{id}/peers")
    public List<VoiceService.ParticipantView> peers(@AuthenticationPrincipal User me, @PathVariable long id) {
        return voice.peers(me, id);
    }
}
