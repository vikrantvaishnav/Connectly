package com.connectly.chat;

import com.connectly.security.RateLimiter;
import com.connectly.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chat;
    private final RateLimiter rateLimiter;

    public ChatController(ChatService chat, RateLimiter rateLimiter) {
        this.chat = chat;
        this.rateLimiter = rateLimiter;
    }

    private void require(boolean allowed) {
        if (!allowed) {
            throw com.connectly.common.error.ApiException.tooManyRequests("Too many requests, slow down");
        }
    }

    public record OpenConversationRequest(Long userId) {}
    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {}

    @GetMapping("/conversations")
    public List<ChatService.ConversationSummary> inbox(@AuthenticationPrincipal User me) {
        return chat.inbox(me);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.ConversationSummary open(@AuthenticationPrincipal User me,
                                                @RequestBody OpenConversationRequest req) {
        require(rateLimiter.allow("chat-open:" + me.getUsername(), 30, 60));
        return chat.openWith(me, req.userId());
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatService.MessageView> messages(@AuthenticationPrincipal User me,
                                                  @PathVariable Long id,
                                                  @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                                                  @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size) {
        return chat.messages(me, id, page, size);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatService.MessageView send(@AuthenticationPrincipal User me,
                                        @PathVariable Long id,
                                        @RequestBody SendMessageRequest req) {
        require(rateLimiter.allow("chat-send:" + me.getUsername(), 60, 60));
        return chat.send(me, id, req.content());
    }

    @PostMapping("/conversations/{id}/read")
    public java.util.Map<String, Object> markRead(@AuthenticationPrincipal User me,
                                                  @PathVariable Long id,
                                                  @RequestBody(required = false) java.util.Map<String, Long> body) {
        Long upTo = body == null ? null : body.get("upToMessageId");
        chat.markRead(me, id, upTo);
        return java.util.Map.of("ok", true);
    }
}
