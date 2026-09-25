package com.connectly.post;

import com.connectly.security.JwtProperties;
import com.connectly.security.RateLimiter;
import com.connectly.security.WebUtil;
import com.connectly.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;
    private final SearchService searchService;
    private final RateLimiter rateLimiter;
    private final JwtProperties props;

    public PostController(PostService postService, SearchService searchService, RateLimiter rateLimiter, JwtProperties props) {
        this.postService = postService;
        this.searchService = searchService;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<PostDtos.PostDto> create(@AuthenticationPrincipal User user,
                                                   @Valid @RequestBody PostDtos.CreatePostRequest req,
                                                   HttpServletRequest http) {
        limit(http, "posts", 30, 60);
        return ResponseEntity.status(201).body(postService.create(user, req));
    }

    @GetMapping("/{id}")
    public PostDtos.PostDto get(@AuthenticationPrincipal User user, @PathVariable long id) {
        return postService.get(user, id);
    }

    @PutMapping("/{id}")
    public PostDtos.PostDto update(@AuthenticationPrincipal User user, @PathVariable long id,
                                   @Valid @RequestBody PostDtos.CreatePostRequest req) {
        return postService.update(user, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable long id) {
        postService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public PostDtos.LikeResponse like(@AuthenticationPrincipal User user, @PathVariable long id) {
        limit(user, "likes", 60, 60);
        return new PostDtos.LikeResponse(postService.toggleLike(user, id));
    }

    @GetMapping("/{id}/comments")
    public List<PostDtos.CommentDto> comments(@AuthenticationPrincipal User user, @PathVariable long id) {
        return postService.comments(user, id);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<PostDtos.CommentDto> comment(@AuthenticationPrincipal User user, @PathVariable long id,
                                                       @Valid @RequestBody PostDtos.AddCommentRequest req) {
        limit(user, "comments", 30, 60);
        return ResponseEntity.status(201).body(postService.addComment(user, id, req));
    }

    // feed endpoints
    @GetMapping("/feed")
    public PostDtos.PostPage feed(@AuthenticationPrincipal User user,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return postService.feed(user, page, size);
    }

    @GetMapping("/explore")
    public PostDtos.PostPage explore(@AuthenticationPrincipal User user,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "12") int size) {
        return postService.explore(user, page, size);
    }

    @GetMapping("/users/{username}")
    public PostDtos.PostPage byAuthor(@AuthenticationPrincipal User user, @PathVariable String username,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "12") int size) {
        return postService.byAuthor(user, username, page, size);
    }

    // ---------- search ----------

    @GetMapping("/search")
    public SearchService.SearchResults search(@AuthenticationPrincipal User user,
                                              @RequestParam("q") String q,
                                              @RequestParam(defaultValue = "5") int userLimit,
                                              @RequestParam(defaultValue = "10") int postLimit) {
        return new SearchService.SearchResults(
                searchService.searchUsers(q, user == null ? null : String.valueOf(user.getId()), userLimit),
                searchService.searchPosts(user, q, postLimit));
    }

    // ---------- saved posts ----------

    @PostMapping("/{id}/save")
    public PostDtos.LikeResponse save(@AuthenticationPrincipal User user, @PathVariable long id) {
        return new PostDtos.LikeResponse(searchService.toggleSave(user, id));
    }

    @GetMapping("/saved")
    public List<PostDtos.PostDto> saved(@AuthenticationPrincipal User user) {
        return searchService.savedFor(user);
    }

    public record LikeResponse(boolean liked) {}

    private void limit(HttpServletRequest http, String action, int n, int windowSec) {
        int[] parsed = RateLimiter.parseSpec(n + "/" + windowSec);
        if (!rateLimiter.allow(action + ":ip:" + WebUtil.clientIp(http), parsed[0], parsed[1])) {
            throw com.connectly.common.error.ApiException.tooManyRequests("Too many requests — slow down.");
        }
    }

    private void limit(User user, String action, int n, int windowSec) {
        int[] parsed = RateLimiter.parseSpec(n + "/" + windowSec);
        if (!rateLimiter.allow(action + ":user:" + user.getId(), parsed[0], parsed[1])) {
            throw com.connectly.common.error.ApiException.tooManyRequests("Too many requests — slow down.");
        }
    }
}
