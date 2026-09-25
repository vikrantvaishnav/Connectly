package com.connectly.user;

import com.connectly.common.error.ApiException;
import com.connectly.post.PostRepository;
import com.connectly.social.ConnectionRepository;
import com.connectly.social.Connection.Status;
import com.connectly.social.FollowRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
            String profileImage, String interests, String lookingFor, Integer age,
            long postCount, long followerCount, long followingCount,
            boolean following, boolean connectedWithMe) {}

    /** ISO date (yyyy-MM-dd) or null. Age is derived — never stored. */
    public record UpdateProfileRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 500) String bio,
            @Size(max = 120) String profession,
            @Size(max = 300) String interests,
            @Size(max = 60) String lookingFor,
            String dateOfBirth,
            /** Only accepts URLs this app generated (see PostService.create). */
            @Pattern(regexp = "^(/media/[A-Za-z0-9._-]{1,80})?$", message = "Invalid image URL") String profileImage) {}

    /** Age in whole years, or null when no (valid) date of birth is on file. */
    private static Integer ageOf(java.time.LocalDate dob) {
        if (dob == null) return null;
        return java.time.Period.between(dob, java.time.LocalDate.now()).getYears();
    }

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
                p != null ? p.getProfileImage() : null,
                p != null ? p.getInterests() : null,
                p != null ? p.getLookingFor() : null,
                p != null ? ageOf(p.getDateOfBirth()) : null,
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
        p.setInterests(normalizeInterests(req.interests()));
        p.setLookingFor(blankToNull(req.lookingFor()));
        if (req.profileImage() != null) {
            p.setProfileImage(blankToNull(req.profileImage()));
        }
        if (req.dateOfBirth() != null) {
            p.setDateOfBirth(parseDob(req.dateOfBirth()));
        }
        profiles.save(p);
        return profile(me, me.getUsername());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Collapse "Coffee, , Hiking" → "coffee, hiking"; cap at 8 tags × 24 chars. */
    private static String normalizeInterests(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = java.util.Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(s -> s.length() > 24 ? s.substring(0, 24) : s)
                .limit(8)
                .collect(java.util.stream.Collectors.joining(", "));
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Rejects dates that imply an unrealistic age — this is a social/dating surface. */
    private static java.time.LocalDate parseDob(String raw) {
        java.time.LocalDate dob;
        try {
            dob = java.time.LocalDate.parse(raw.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("Date of birth must be yyyy-MM-dd");
        }
        int age = ageOf(dob);
        if (age < 18) throw ApiException.badRequest("You must be at least 18 to use Connectly");
        if (age > 110) throw ApiException.badRequest("Please enter a valid date of birth");
        return dob;
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
