package com.connectly.user;

import com.connectly.common.error.ApiException;
import com.connectly.post.PostRepository;
import com.connectly.social.ConnectionRepository;
import com.connectly.social.Connection.Status;
import com.connectly.social.FollowRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PostRepository posts;
    private final FollowRepository follows;
    private final ConnectionRepository connections;

    public UserController(UserRepository users, UserProfileRepository profiles, PostRepository posts,
                         FollowRepository follows, ConnectionRepository connections) {
        this.users = users;
        this.profiles = profiles;
        this.posts = posts;
        this.follows = follows;
        this.connections = connections;
    }

 public record ProfileDto(
            long id, String username, String firstName, String lastName, String bio, String profession,
            long postCount, long followerCount, long followingCount,
            boolean following, boolean connectedWithMe) {}

    public record UpdateProfileRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 500) String bio,
            @Size(max = 120) String profession) {}

    @GetMapping("/{username}")
    @Transactional(readOnly = true)
    public ProfileDto profile(@AuthenticationPrincipal User viewer,
                              @PathVariable String username) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(() -> ApiException.notFound("User not found"));
        UserProfile p = profiles.findByUserId(user.getId()).orElse(null);
        boolean following = viewer != null
                && follows.existsByFollowerIdAndFolloweeId(viewer.getId(), user.getId());
        boolean connected = viewer != null
                && connections.findBetween(viewer.getId(), user.getId())
                        .map(c -> c.getStatus() == Status.ACCEPTED).orElse(false);
        return new ProfileDto(
                user.getId(), user.getUsername(),
                p != null ? p.getFirstName() : null, p != null ? p.getLastName() : null,
                p != null ? p.getBio() : null, p != null ? p.getProfession() : null,
                posts.countByAuthorId(user.getId()),
                follows.countByFolloweeId(user.getId()),
                follows.countByFollowerId(user.getId()),
                following, connected);
    }

    @PutMapping("/me")
    @Transactional
    public ProfileDto updateMe(@AuthenticationPrincipal User me,
                               @Valid @RequestBody UpdateProfileRequest req) {
        UserProfile p = profiles.findByUserId(me.getId()).orElseGet(() -> {
            UserProfile fresh = new UserProfile();
            fresh.setUserId(me.getId());
            return fresh;
        });
        p.setFirstName(req.firstName());
        p.setLastName(req.lastName());
        p.setBio(req.bio());
        p.setProfession(req.profession());
        profiles.save(p);
        return profile(me, me.getUsername());
    }

    /** Tiny response shape used by /me. Kept here to avoid another DTO class. */
    public record MeDto(long id, String username, String email, String role, boolean emailVerified,
                        boolean totpEnabled, String firstName, String lastName, String bio) {}

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal User me) {
        UserProfile p = profiles.findByUserId(me.getId()).orElse(null);
        return new MeDto(me.getId(), me.getUsername(), me.getEmail(), me.getRole().name(),
                me.isEmailVerified(), me.isTotpEnabled(),
                p != null ? p.getFirstName() : null, p != null ? p.getLastName() : null,
                p != null ? p.getBio() : null);
    }
}
