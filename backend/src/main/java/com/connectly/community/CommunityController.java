package com.connectly.community;

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
@RequestMapping("/api/v1/communities")
public class CommunityController {

    private final CommunityService communities;
    private final RateLimiter rateLimiter;

    public CommunityController(CommunityService communities, RateLimiter rateLimiter) {
        this.communities = communities;
        this.rateLimiter = rateLimiter;
    }

    public record CreateCommunityRequest(@NotBlank @Size(min = 3, max = 60) String name,
                                         @Size(max = 500) String description,
                                         @Size(max = 8) String icon) {}

    public record CreateChannelRequest(@NotBlank @Size(min = 2, max = 40) String name,
                                       @Size(max = 200) String topic) {}

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {}

    private void limit(String key, int n, int window) {
        if (!rateLimiter.allow(key, n, window)) {
            throw com.connectly.common.error.ApiException.tooManyRequests("Too many requests, slow down");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.CommunityView create(@AuthenticationPrincipal User me,
                                                 @RequestBody CreateCommunityRequest req) {
        limit("community-create:" + me.getUsername(), 5, 3600);
        return communities.create(me, req.name(), req.description(), req.icon());
    }

    @GetMapping
    public List<CommunityService.CommunityView> browse(@AuthenticationPrincipal User me) {
        return communities.browse(me);
    }

    @GetMapping("/{id}")
    public CommunityService.CommunityView get(@AuthenticationPrincipal User me, @PathVariable long id) {
        return communities.get(me, id);
    }

    @PostMapping("/{id}/join")
    public CommunityService.CommunityView join(@AuthenticationPrincipal User me, @PathVariable long id) {
        limit("community-join:" + me.getUsername(), 20, 60);
        return communities.join(me, id);
    }

    @DeleteMapping("/{id}/join")
    public Map<String, Object> leave(@AuthenticationPrincipal User me, @PathVariable long id) {
        communities.leave(me, id);
        return Map.of("ok", true);
    }

    @GetMapping("/{id}/channels")
    public List<CommunityService.ChannelView> channels(@AuthenticationPrincipal User me, @PathVariable long id) {
        return communities.channels(me, id);
    }

    @PostMapping("/{id}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.ChannelView createChannel(@AuthenticationPrincipal User me, @PathVariable long id,
                                                      @RequestBody CreateChannelRequest req) {
        limit("channel-create:" + me.getUsername(), 10, 3600);
        return communities.createChannel(me, id, req.name(), req.topic());
    }

    @GetMapping("/{id}/members")
    public List<CommunityService.MemberView> members(@AuthenticationPrincipal User me, @PathVariable long id) {
        return communities.members(me, id);
    }

    @GetMapping("/channels/{channelId}/messages")
    public List<CommunityService.ChannelMessageView> messages(@AuthenticationPrincipal User me,
                                                              @PathVariable long channelId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "50") int size) {
        return communities.messages(me, channelId, page, size);
    }

    @PostMapping("/channels/{channelId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityService.ChannelMessageView send(@AuthenticationPrincipal User me,
                                                    @PathVariable long channelId,
                                                    @RequestBody SendMessageRequest req) {
        limit("channel-send:" + me.getUsername(), 90, 60);
        return communities.sendMessage(me, channelId, req.content());
    }
}
