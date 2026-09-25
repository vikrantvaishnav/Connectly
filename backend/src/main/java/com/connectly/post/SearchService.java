package com.connectly.post;

import com.connectly.common.error.ApiException;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    public record UserHit(long id, String username, String firstName, String lastName, String profession,
                          String profileImage) {}

    public record SearchResults(List<UserHit> users, List<PostDtos.PostDto> posts) {}

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final SavedPostRepository saved;
    private final PostService postService;
    private final com.connectly.social.SafetyService safety;

    public SearchService(UserRepository users, UserProfileRepository profiles, PostRepository posts,
                         PostLikeRepository likes, SavedPostRepository saved, PostService postService,
                         com.connectly.social.SafetyService safety) {
        this.users = users;
        this.profiles = profiles;
        this.posts = posts;
        this.likes = likes;
        this.saved = saved;
        this.postService = postService;
        this.safety = safety;
    }

    /** Users by username or profile name — hiding blocked (either direction) and muted users. */
    @Transactional(readOnly = true)
    public List<UserHit> searchUsers(String q, String viewerId, int limit) {
        String term = q.strip().toLowerCase();
        if (term.isEmpty()) return List.of();
        List<User> hits = users.searchUsers(term, PageRequest.of(0, Math.max(1, Math.min(limit, 20))));
        // Search safety: blocked-involving and muted users never surface.
        if (viewerId != null && !hits.isEmpty()) {
            java.util.Set<Long> hidden = safety.hiddenAuthorIds(Long.parseLong(viewerId));
            hits = hits.stream().filter(u -> !hidden.contains(u.getId())).toList();
        }
        // One batched profile load for the whole result set.
        Map<Long, UserProfile> profileById = new HashMap<>();
        if (!hits.isEmpty()) {
            for (UserProfile p : profiles.findByUserIdIn(hits.stream().map(User::getId).toList())) {
                profileById.put(p.getUserId(), p);
            }
        }
        return hits.stream()
                .map(u -> {
                    UserProfile p = profileById.get(u.getId());
                    return new UserHit(u.getId(), u.getUsername(),
                            p != null ? p.getFirstName() : null,
                            p != null ? p.getLastName() : null,
                            p != null ? p.getProfession() : null,
                            p != null ? p.getProfileImage() : null);
                })
                .toList();
    }

    /** Public posts matching the term — never exposes FOLLOWERS/PRIVATE or blocked users' content. */
    @Transactional(readOnly = true)
    public List<PostDtos.PostDto> searchPosts(User viewer, String q, int limit) {
        String term = q.strip();
        if (term.isEmpty()) return List.of();
        List<Post> hits = posts.searchPublicByContent(term, PageRequest.of(0, Math.max(1, Math.min(limit, 20))));
        if (hits.isEmpty()) return List.of();
        // Search safety: filter blocked/muted authors out of the result set.
        if (viewer != null) {
            java.util.Set<Long> hidden = safety.hiddenAuthorIds(viewer.getId());
            hits = hits.stream().filter(p -> !hidden.contains(p.getAuthor().getId())).toList();
            if (hits.isEmpty()) return List.of();
        }
        List<Long> postIds = hits.stream().map(Post::getId).toList();
        // Batched profiles + like counts (was one profile query and one count per hit).
        Map<Long, UserProfile> profileById = new HashMap<>();
        for (UserProfile p : profiles.findByUserIdIn(
                hits.stream().map(p -> p.getAuthor().getId()).distinct().toList())) {
            profileById.put(p.getUserId(), p);
        }
        Map<Long, Long> likeCounts = new HashMap<>();
        for (Object[] row : likes.countByPostIdIn(postIds)) {
            likeCounts.put((Long) row[0], (Long) row[1]);
        }
        return hits.stream()
                .map(p -> PostDtos.PostDto.from(p,
                        PostDtos.AuthorDto.from(p.getAuthor(), profileById.get(p.getAuthor().getId())),
                        likeCounts.getOrDefault(p.getId(), 0L), 0, false,
                        viewer != null && viewer.getId().equals(p.getAuthor().getId())))
                .toList();
    }

    // ---------- saved posts ----------

    @Transactional
    public boolean toggleSave(User actor, long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        // you may only save posts you can see
        postService.get(actor, postId);
        return saved.findByPostIdAndUserId(postId, actor.getId())
                .map(existing -> {
                    saved.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    SavedPost s = new SavedPost();
                    s.setPost(post);
                    s.setUser(actor);
                    saved.save(s);
                    return true;
                });
    }

    /** Saved posts, newest first — batched (was one full post DTO build per row). */
    @Transactional(readOnly = true)
    public List<PostDtos.PostDto> savedFor(User actor) {
        List<Long> ids = saved.findByUserIdOrderByCreatedAtDesc(actor.getId()).stream()
                .map(s -> s.getPost().getId())
                .toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Post> byId = new HashMap<>();
        for (Post p : posts.findAllWithAuthorByIdIn(ids)) {
            byId.put(p.getId(), p);
        }
        List<Post> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return postService.mapForViewer(ordered, actor);
    }
}
