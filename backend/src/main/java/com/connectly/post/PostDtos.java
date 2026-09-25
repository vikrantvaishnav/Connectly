package com.connectly.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PostDtos {

    private PostDtos() {}

    public record CreatePostRequest(
            @NotBlank @Size(min = 1, max = 2000) String content,
            @NotNull @Pattern(regexp = "PUBLIC|FOLLOWERS|PRIVATE") String visibility,
            String imageUrl) {
    }

    public record AddCommentRequest(
            @NotBlank @Size(min = 1, max = 1000) String content,
            Long parentCommentId) {
    }

    public record AuthorDto(long id, String username, String firstName, String lastName, String profileImage) {
        public static AuthorDto from(com.connectly.user.User u, com.connectly.user.UserProfile p) {
            return new AuthorDto(u.getId(), u.getUsername(),
                    p != null ? p.getFirstName() : null,
                    p != null ? p.getLastName() : null,
                    p != null ? p.getProfileImage() : null);
        }
    }

    public record CommentDto(long id, AuthorDto author, String content, Long parentCommentId, Instant createdAt) {
        public static CommentDto from(Comment c, com.connectly.user.UserProfile profile) {
            return new CommentDto(c.getId(), AuthorDto.from(c.getAuthor(), profile), c.getContent(),
                    c.getParent() != null ? c.getParent().getId() : null, c.getCreatedAt());
        }
    }

    public record PostDto(
            long id,
            AuthorDto author,
            String content,
            String imageUrl,
            String visibility,
            Instant createdAt,
            Instant updatedAt,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            boolean canEdit) {

        public static PostDto from(Post p, AuthorDto author, long likeCount, long commentCount,
                                   boolean likedByMe, boolean canEdit) {
            return new PostDto(p.getId(), author, p.getContent(), p.getImageUrl(),
                    p.getVisibility().name(), p.getCreatedAt(), p.getUpdatedAt(),
                    likeCount, commentCount, likedByMe, canEdit);
        }
    }

    public record PostPage(List<PostDto> posts, int page, int size, boolean hasNext) {}

    public record LikeResponse(boolean liked) {}
}
