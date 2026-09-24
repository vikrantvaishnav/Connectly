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

    public record UserHit(long id, String username, String firstName, String lastName, String profession) {}

    public record SearchResults(List<UserHit> users, List<PostDtos.PostDto> posts) {}

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final SavedPostRepository saved;
    private final PostService postService;

    public SearchService(UserRepository users, UserProfileRepository profiles, PostRepository posts,
                         PostLikeRepository likes, SavedPostRepository saved, PostService postService) {
        this.users = users;
        this.profiles = profiles;
        this.posts = posts;
        this.likes = likes;
        this.saved = saved;
        this.postService = postService;
    }

    /** Users by username or profile name. Case-insensitive, prefix-friendly. */
    @Transactional(readOnly = true)
    public List<UserHit> searchUsers(String q, int limit) {
        String term = q.strip().toLowerCase();
        if (term.isEmpty()) return List.of();
        return users.searchUsers(term, PageRequest.of(0, Math.max(1, Math.min(limit, 20)))).stream()
                .map(u -> {
                    UserProfile p = profiles.findByUserId(u.getId()).orElse(null);
                    return new UserHit(u.getId(), u.getUsername(),
                            p != null ? p.getFirstName() : null,
                            p != null ? p.getLastName() : null,
                            p != null ? p.getProfession() : null);
                })
                .toList();
    }

    /** Public posts matching the term — never exposes FOLLOWERS/PRIVATE content via search. */
    @Transactional(readOnly = true)
    public List<PostDtos.PostDto> searchPosts(User viewer, String q, int limit) {
        String term = q.strip();
        if (term.isEmpty()) return List.of();
        List<Post> hits = posts.searchPublicByContent(term, PageRequest.of(0, Math.max(1, Math.min(limit, 20))));
        Map<Long, UserProfile> profileMap = new HashMap<>();
        for (Post p : hits) {
            profileMap.computeIfAbsent(p.getAuthor().getId(),
                    id -> profiles.findByUserId(id).orElse(null));
        }
        return hits.stream()
                .map(p -> PostDtos.PostDto.from(p,
                        PostDtos.AuthorDto.from(p.getAuthor(), profileMap.get(p.getAuthor().getId())),
                        likes.countByPostId(p.getId()), 0, false,
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

    @Transactional(readOnly = true)
    public List<PostDtos.PostDto> savedFor(User actor) {
        return saved.findByUserIdOrderByCreatedAtDesc(actor.getId()).stream()
                .map(SavedPost::getPost)
                .map(p -> postService.get(actor, p.getId()))
                .toList();
    }
}
