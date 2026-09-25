package com.connectly.nearby;

import com.connectly.common.error.ApiException;
import com.connectly.presence.PresenceService;
import com.connectly.social.Connection;
import com.connectly.social.ConnectionRepository;
import com.connectly.social.FollowRepository;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nearby-people discovery: the "who's around me" surface behind Nearby and Discover.
 *
 * Privacy invariants (unchanged, and worth preserving):
 *  1. Raw coordinates are never returned by any endpoint — only rounded distances.
 *  2. Users with discoverable=false are invisible, even to admins' nearby queries.
 *  3. Radius is server-clamped to sane bounds (clients can't ask for the whole planet).
 *  4. Stale locations (older than 7 days) are treated as unknown and hidden.
 *
 * Performance: every card is built from four batched queries (users, profiles,
 * follows, connections) plus the presence map — never a per-candidate round trip.
 */
@Service
public class NearbyService {

    /** Max distance returned to the client — coarser = more privacy. */
    private static final double MAX_RADIUS_KM = 50;
    private static final Duration STALE_AFTER = Duration.ofDays(7);
    private static final int DISCOVER_PAGE = 30;

    public record NearbyHit(
            long id,
            String username,
            String firstName,
            String lastName,
            String profession,
            String profileImage,
            String bio,
            List<String> interests,
            String lookingFor,
            Integer age,
            double distanceKm,
            boolean followingMe,
            boolean online,
            /** NONE | PENDING_INCOMING | PENDING_OUTGOING | ACCEPTED */
            String connectionStatus) {}

    /** A Discover card: same person, plus what you have in common (for highlighting). */
    public record DiscoverCard(NearbyHit person, int sharedInterests, List<String> sharedTags, int score) {}

    private final UserLocationRepository locations;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FollowRepository follows;
    private final ConnectionRepository connections;
    private final PresenceService presence;
    private final com.connectly.social.SafetyService safety;

    public NearbyService(UserLocationRepository locations, UserRepository users,
                         UserProfileRepository profiles, FollowRepository follows,
                         ConnectionRepository connections, PresenceService presence,
                         com.connectly.social.SafetyService safety) {
        this.locations = locations;
        this.users = users;
        this.profiles = profiles;
        this.follows = follows;
        this.connections = connections;
        this.presence = presence;
        this.safety = safety;
    }

    // ---------- location writes ----------

    @Transactional
    public void updateLocation(User actor, Double latitude, Double longitude, boolean discoverable) {
        if (latitude == null || longitude == null) {
            throw ApiException.badRequest("Latitude and longitude are required");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw ApiException.badRequest("Coordinates out of range");
        }
        UserLocation loc = locations.findByUserId(actor.getId()).orElseGet(() -> {
            UserLocation fresh = new UserLocation();
            fresh.setUserId(actor.getId());
            return fresh;
        });
        loc.setLatitude(java.math.BigDecimal.valueOf(latitude));
        loc.setLongitude(java.math.BigDecimal.valueOf(longitude));
        loc.setDiscoverable(discoverable);
        locations.save(loc);
    }

    @Transactional
    public void setDiscoverable(User actor, boolean discoverable) {
        UserLocation loc = locations.findByUserId(actor.getId())
                .orElseThrow(() -> ApiException.notFound("No location saved — share your location first"));
        loc.setDiscoverable(discoverable);
        locations.save(loc);
    }

    @Transactional(readOnly = true)
    public NearbyStatus status(User actor) {
        UserLocation loc = locations.findByUserId(actor.getId()).orElse(null);
        boolean hasLocation = loc != null && loc.getLatitude() != null && loc.getLongitude() != null;
        boolean fresh = hasLocation && loc.getUpdatedAt() != null &&
                loc.getUpdatedAt().isAfter(Instant.now().minus(STALE_AFTER));
        return new NearbyStatus(hasLocation && fresh, loc != null && loc.isDiscoverable());
    }

    public record NearbyStatus(boolean locationShared, boolean discoverable) {}

    // ---------- discovery reads ----------

    @Transactional(readOnly = true)
    public List<NearbyHit> findNearby(User viewer, double lat, double lng, double radiusKm) {
        double clamped = Math.min(radiusKm, MAX_RADIUS_KM);
        return candidates(viewer, lat, lng, clamped, false).stream()
                .limit(50)
                .map(Candidate::hit)
                .toList();
    }

    /**
     * Dating-style deck: people near you who are not already a match, best first.
     * Ranking blends how much you have in common with how close they are, so two
     * shared interests beat a slightly nearer stranger. Already-matched people are
     * excluded — those belong in Messages, not the deck.
     */
    @Transactional(readOnly = true)
    public List<DiscoverCard> suggestions(User viewer, double lat, double lng, double radiusKm) {
        double clamped = Math.min(radiusKm, MAX_RADIUS_KM);
        List<DiscoverCard> cards = new ArrayList<>();
        for (Candidate c : candidates(viewer, lat, lng, clamped, true)) {
            int shared = c.sharedTags().size();
            // Complete profiles rank first; a shared tag is worth ~5 km of proximity.
            int filled = (c.profile() != null && c.profile().getProfileImage() != null ? 1 : 0)
                    + (c.profile() != null && c.profile().getBio() != null ? 1 : 0);
            int score = shared * 5 + filled + (c.followingMe() ? 2 : 0)
                    - (int) Math.round(c.distanceKm() / 4.0);
            cards.add(new DiscoverCard(c.hit(), shared, c.sharedTags(), score));
        }
        cards.sort(Comparator.comparingInt(DiscoverCard::score).reversed()
                .thenComparingDouble(dc -> dc.person().distanceKm()));
        return cards.stream().limit(DISCOVER_PAGE).toList();
    }


    // ---------- batched candidate assembly ----------

    private record Candidate(UserLocation loc, User user, UserProfile profile, double distanceKm,
                             boolean followingMe, String connectionStatus, boolean online,
                             List<String> allTags, List<String> sharedTags) {

        NearbyHit hit() {
            return new NearbyHit(
                    user != null ? user.getId() : loc.getUserId(),
                    user != null ? user.getUsername() : "?",
                    profile != null ? profile.getFirstName() : null,
                    profile != null ? profile.getLastName() : null,
                    profile != null ? profile.getProfession() : null,
                    profile != null ? profile.getProfileImage() : null,
                    profile != null ? profile.getBio() : null,
                    allTags,
                    profile != null ? profile.getLookingFor() : null,
                    profile != null ? ageOf(profile.getDateOfBirth()) : null,
                    Math.round(distanceKm * 10) / 10.0,
                    followingMe,
                    online,
                    connectionStatus);
        }
    }

    /** Builds every visible candidate within {@code maxKm} using a fixed number of queries. */
    private List<Candidate> candidates(User viewer, double lat, double lng, double maxKm, boolean excludeMatched) {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        List<UserLocation> fresh = locations.findByDiscoverableTrue().stream()
                // never show yourself
                .filter(loc -> !loc.getUserId().equals(viewer.getId()))
                // skip people who never shared coordinates or went stale
                .filter(loc -> loc.getLatitude() != null && loc.getLongitude() != null)
                .filter(loc -> loc.getUpdatedAt() != null && loc.getUpdatedAt().isAfter(cutoff))
                .toList();
        if (fresh.isEmpty()) return List.of();

        List<Long> ids = fresh.stream().map(UserLocation::getUserId).distinct().toList();

        Map<Long, User> userById = new HashMap<>();
        for (User u : users.findAllById(ids)) {
            userById.put(u.getId(), u);
        }
        Map<Long, UserProfile> profileById = new HashMap<>();
        for (UserProfile p : profiles.findByUserIdIn(ids)) {
            profileById.put(p.getUserId(), p);
        }
        Set<Long> followersOfMe = new HashSet<>(follows.findFollowerIdsAmong(viewer.getId(), ids));

        Map<Long, String> connByOther = new HashMap<>();
        for (Connection c : connections.findAllBetween(viewer.getId(), ids)) {
            boolean mine = c.getSender().getId().equals(viewer.getId());
            Long other = mine ? c.getReceiver().getId() : c.getSender().getId();
            connByOther.put(other, statusOf(c, mine));
        }

        Set<Long> online = presence.onlineAmong(ids);
        // Safety: blocked (either direction) and muted users never appear nearby.
        Set<Long> hidden = safety.hiddenAuthorIds(viewer.getId());
        // The viewer is never among the candidates, so load their own tags separately.
        Set<String> mine = interestsOf(profiles.findByUserId(viewer.getId()).orElse(null));

        List<Candidate> out = new ArrayList<>(fresh.size());
        for (UserLocation loc : fresh) {
            Long otherId = loc.getUserId();
            if (hidden.contains(otherId)) continue;
            String status = connByOther.getOrDefault(otherId, "NONE");
            if (excludeMatched && "ACCEPTED".equals(status)) continue;
            double d = haversineKm(lat, lng, loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
            if (d > maxKm) continue;
            Set<String> theirs = interestsOf(profileById.get(otherId));
            List<String> shared = new ArrayList<>();
            for (String tag : theirs) {
                if (mine.contains(tag)) shared.add(tag);
            }
            out.add(new Candidate(loc, userById.get(otherId), profileById.get(otherId), d,
                    followersOfMe.contains(otherId), status, online.contains(otherId),
                    List.copyOf(theirs), shared));
        }
        out.sort(Comparator.comparingDouble(Candidate::distanceKm));
        return out;
    }

    private static String statusOf(Connection c, boolean iAmSender) {
        return switch (c.getStatus()) {
            case ACCEPTED -> "ACCEPTED";
            case PENDING -> iAmSender ? "PENDING_OUTGOING" : "PENDING_INCOMING";
            default -> "NONE";
        };
    }

    private static Integer ageOf(LocalDate dob) {
        if (dob == null) return null;
        return Period.between(dob, LocalDate.now()).getYears();
    }

    /** Parses the stored comma-separated tags into a lowercase set for matching. */
    private static Set<String> interestsOf(UserProfile p) {
        if (p == null || p.getInterests() == null || p.getInterests().isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String raw : p.getInterests().split(",")) {
            String tag = raw.strip().toLowerCase(java.util.Locale.ROOT);
            if (!tag.isEmpty()) out.add(tag);
        }
        return out;
    }

    /** Great-circle distance in km. */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
