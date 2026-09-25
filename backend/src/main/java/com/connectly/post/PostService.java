package com.connectly.post;

import com.connectly.common.error.ApiException;
import com.connectly.social.ConnectionRepository;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PostService {

    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final CommentRepository comments;
    private final com.connectly.social.FollowRepository follows;
    private final ConnectionRepository connections;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final com.connectly.notification.NotificationService notifier;

    public PostService(PostRepository posts, PostLikeRepository likes, CommentRepository comments,
                       com.connectly.social.FollowRepository follows, ConnectionRepository connections,
                       UserRepository users, UserProfileRepository profiles,
                       com.connectly.notification.NotificationService notifier) {
        this.posts = posts;
        this.likes = likes;
        this.comments = comments;
        this.follows = follows;
        this.connections = connections;
        this.users = users;
        this.profiles = profiles;
        this.notifier = notifier;
    }

    // ---------- writes ----------

    @Transactional
    public PostDtos.PostDto create(User author, PostDtos.CreatePostRequest req) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(req.content().strip());
        if (req.imageUrl() != null && !req.imageUrl().isBlank()) {
            // Only accept URLs this app generated (never arbitrary remote URLs).
            String url = req.imageUrl().strip();
            if (!url.matches("/media/[A-Za-z0-9._-]{1,80}")) {
                throw ApiException.badRequest("Invalid image URL");
            }
            post.setImageUrl(url);
        }
        post.setVisibility(Post.Visibility.valueOf(req.visibility().toUpperCase(Locale.ROOT)));
        posts.save(post);
        return toDto(post, author, 0, 0, false);
    }

    @Transactional
    public PostDtos.PostDto update(User actor, long postId, PostDtos.CreatePostRequest req) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        requireCanEdit(post, actor);
        post.setContent(req.content().strip());
        post.setVisibility(Post.Visibility.valueOf(req.visibility().toUpperCase(Locale.ROOT)));
        return toDto(post, actor, likes.countByPostId(postId), comments.countByPostId(postId),
                likes.existsByPostIdAndUserId(postId, actor.getId()));
    }

    /** Object-level authorization: owner or admin — nobody else. */
    @Transactional
    public void delete(User actor, long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        requireCanEdit(post, actor);
        posts.delete(post);
    }

    @Transactional
    public boolean toggleLike(User actor, long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        requireCanView(post, actor);
        return likes.findByPostIdAndUserId(postId, actor.getId())
                .map(existing -> {
                    likes.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    PostLike like = new PostLike();
                    like.setPost(post);
                    like.setUser(actor);
                    likes.save(like);
                    notifier.notify(post.getAuthor(), actor,
                            com.connectly.notification.NotificationService.LIKE, "post", postId);
                    return true;
                });
    }

    @Transactional
    public PostDtos.CommentDto addComment(User actor, long postId, PostDtos.AddCommentRequest req) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        requireCanView(post, actor);
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(actor);
        comment.setContent(req.content().strip());
        if (req.parentCommentId() != null) {
            Comment parent = comments.findById(req.parentCommentId())
                    .orElseThrow(() -> ApiException.badRequest("Parent comment not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw ApiException.badRequest("Parent comment belongs to a different post");
            }
            comment.setParent(parent);
        }
        comments.save(comment);
        notifier.notify(post.getAuthor(), actor,
                com.connectly.notification.NotificationService.COMMENT, "post", postId);
        return PostDtos.CommentDto.from(comment, profiles.findByUserId(actor.getId()).orElse(null));
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public PostDtos.PostDto get(User viewer, long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        requireCanView(post, viewer);
        return toDto(post, viewer,
                likes.countByPostId(postId),
                comments.countByPostId(postId),
                viewer != null && likes.existsByPostIdAndUserId(postId, viewer.getId()));
    }

    @Transactional(readOnly = true)
    public List<PostDtos.CommentDto> comments(User viewer, long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        requireCanView(post, viewer);
        Map<Long, UserProfile> profileMap = profileMap(comments.findByPostIdWithAuthor(postId)
                .stream().map(c -> c.getAuthor().getId()).distinct().toList());
        return comments.findByPostIdWithAuthor(postId).stream()
                .map(c -> PostDtos.CommentDto.from(c, profileMap.get(c.getAuthor().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PostDtos.PostPage feed(User viewer, int page, int size) {
        List<Long> authorIds = new java.util.ArrayList<>(
                follows.findByFollowerId(viewer.getId()).stream().map(f -> f.getFollowee().getId()).toList());
        authorIds.add(viewer.getId()); // home feed includes your own posts
        Page<Post> result = posts.feedFor(authorIds, PageRequest.of(page, Math.min(size, 50)));
        return toPage(result, viewer);
    }

    @Transactional(readOnly = true)
    public PostDtos.PostPage explore(User viewer, int page, int size) {
        Page<Post> result = posts.findByVisibilityOrderByCreatedAtDesc(Post.Visibility.PUBLIC,
                PageRequest.of(page, Math.min(size, 50)));
        return toPage(result, viewer);
    }

    @Transactional(readOnly = true)
    public PostDtos.PostPage byAuthor(User viewer, String username, int page, int size) {
        User author = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        Page<Post> result = posts.findByAuthorIdOrderByCreatedAtDesc(author.getId(),
                PageRequest.of(page, Math.min(size, 50)));
        List<Post> visible = result.getContent().stream()
                .filter(p -> {
                    try {
                        requireCanView(p, viewer);
                        return true;
                    } catch (ApiException e) {
                        return false; // skip posts this viewer can't see
                    }
                })
                .toList();
        return new PostDtos.PostPage(mapPosts(visible, viewer), page, size, result.hasNext());
    }

    // ---------- authorization ----------

    /** Visibility rule: PUBLIC anyone; FOLLOWERS needs a follow edge; PRIVATE owner only. 404 — never 403 — so we don't leak existence. */
    private void requireCanView(Post post, User viewer) {
        if (viewer != null && viewer.getId().equals(post.getAuthor().getId())) return;
        if (viewer != null && viewer.getRole() == User.Role.ADMIN) return;
        switch (post.getVisibility()) {
            case PUBLIC -> {}
            case FOLLOWERS -> {
                if (viewer == null || !follows.existsByFollowerIdAndFolloweeId(viewer.getId(), post.getAuthor().getId())) {
                    throw ApiException.notFound("Post not found");
                }
            }
            case PRIVATE -> throw ApiException.notFound("Post not found");
        }
    }

    private void requireCanEdit(Post post, User actor) {
        boolean owner = actor.getId().equals(post.getAuthor().getId());
        boolean admin = actor.getRole() == User.Role.ADMIN;
        if (!owner && !admin) {
            throw ApiException.forbidden("You can only modify your own posts");
        }
    }

    // ---------- mapping (batched — 4 queries per page, not per post) ----------

    private Map<Long, UserProfile> profileMap(List<Long> userIds) {
        Map<Long, UserProfile> map = new HashMap<>();
        if (userIds.isEmpty()) return map;
        for (UserProfile p : profiles.findByUserIdIn(userIds)) {
            map.put(p.getUserId(), p);
        }
        return map;
    }

    private PostDtos.PostDto toDto(Post p, User viewer, long likeCount, long commentCount, boolean likedByMe) {
        UserProfile profile = profiles.findByUserId(p.getAuthor().getId()).orElse(null);
        PostDtos.AuthorDto author = PostDtos.AuthorDto.from(p.getAuthor(), profile);
        boolean canEdit = viewer != null &&
                (viewer.getId().equals(p.getAuthor().getId()) || viewer.getRole() == User.Role.ADMIN);
        return PostDtos.PostDto.from(p, author, likeCount, commentCount, likedByMe, canEdit);
    }

    /** Maps a page of posts using batched lookups: profiles, like counts, comment counts, liked-by-me. */
    private List<PostDtos.PostDto> mapPosts(List<Post> content, User viewer) {
        if (content.isEmpty()) return List.of();
        List<Long> postIds = content.stream().map(Post::getId).toList();
        List<Long> authorIds = content.stream().map(p -> p.getAuthor().getId()).distinct().toList();
        Map<Long, UserProfile> profileById = profileMap(authorIds);
        Map<Long, Long> likeCounts = new HashMap<>();
        for (Object[] row : likes.countByPostIdIn(postIds)) {
            likeCounts.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : comments.countByPostIdIn(postIds)) {
            commentCounts.put((Long) row[0], (Long) row[1]);
        }
        java.util.Set<Long> likedByMe = viewer != null
                ? new java.util.HashSet<>(likes.findPostIdsLikedBy(viewer.getId(), postIds))
                : java.util.Set.of();
        return content.stream()
                .map(p -> {
                    PostDtos.AuthorDto author = PostDtos.AuthorDto.from(p.getAuthor(), profileById.get(p.getAuthor().getId()));
                    boolean canEdit = viewer != null &&
                            (viewer.getId().equals(p.getAuthor().getId()) || viewer.getRole() == User.Role.ADMIN);
                    return PostDtos.PostDto.from(p, author,
                            likeCounts.getOrDefault(p.getId(), 0L),
                            commentCounts.getOrDefault(p.getId(), 0L),
                            likedByMe.contains(p.getId()), canEdit);
                })
                .toList();
    }

    /** Public wrapper over the batched mapper so other services reuse one code path. */
    @Transactional(readOnly = true)
    public List<PostDtos.PostDto> mapForViewer(List<Post> posts, User viewer) {
        return mapPosts(posts, viewer);
    }

    private PostDtos.PostPage toPage(Page<Post> result, User viewer) {
        return new PostDtos.PostPage(mapPosts(result.getContent(), viewer),
                result.getNumber(), result.getSize(), result.hasNext());
    }
}
