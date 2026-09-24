package com.connectly.nearby;

import com.connectly.common.error.ApiException;
import com.connectly.social.FollowRepository;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Nearby-people discovery. Privacy invariants:
 *  1. Raw coordinates are never returned by any endpoint — only rounded distances.
 *  2. Users with discoverable=false are invisible, even to admins' nearby queries.
 *  3. Radius is server-clamped to sane bounds (clients can't ask for the whole planet).
 *  4. Stale locations (older than 7 days) are treated as unknown and hidden.
 */
@Service
public class NearbyService {

    /** Max distance returned to the client — coarser = more privacy. */
    private static final double MAX_RADIUS_KM = 50;
    private static final java.time.Duration STALE_AFTER = java.time.Duration.ofDays(7);

    public record NearbyHit(
            long id,
            String username,
            String firstName,
            String lastName,
            String profession,
            double distanceKm,
            boolean followingMe) {}

    private final UserLocationRepository locations;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FollowRepository follows;

    public NearbyService(UserLocationRepository locations, UserRepository users,
                         UserProfileRepository profiles, FollowRepository follows) {
        this.locations = locations;
        this.users = users;
        this.profiles = profiles;
        this.follows = follows;
    }

    @Transactional
    public void updateLocation(User actor, Double latitude, Double longitude, boolean discoverable) {
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

    @Transactional
    public NearbyStatus status(User actor) {
        UserLocation loc = locations.findByUserId(actor.getId()).orElse(null);
        boolean hasLocation = loc != null && loc.getLatitude() != null && loc.getLongitude() != null;
        boolean fresh = hasLocation && loc.getUpdatedAt() != null &&
                loc.getUpdatedAt().isAfter(java.time.Instant.now().minus(STALE_AFTER));
        return new NearbyStatus(hasLocation && fresh, loc != null && loc.isDiscoverable());
    }

    public record NearbyStatus(boolean locationShared, boolean discoverable) {}

    @Transactional
    public List<NearbyHit> findNearby(User viewer, double lat, double lng, double radiusKm) {
        double clamped = Math.min(radiusKm, MAX_RADIUS_KM);
        List<UserLocation> candidates = locations.findByDiscoverableTrue();

        Map<Long, UserProfile> profileMap = candidates.stream()
                .map(UserLocation::getUserId)
                .collect(Collectors.toMap(id -> id, id -> profiles.findByUserId(id).orElse(null), (a, b) -> a));

        return candidates.stream()
                // never show yourself
                .filter(loc -> !loc.getUserId().equals(viewer.getId()))
                // skip people who never shared coordinates or went stale
                .filter(loc -> loc.getLatitude() != null && loc.getLongitude() != null)
                .filter(loc -> loc.getUpdatedAt() != null &&
                        loc.getUpdatedAt().isAfter(java.time.Instant.now().minus(STALE_AFTER)))
                .map(loc -> {
                    double d = haversineKm(lat, lng, loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
                    if (d > clamped) return null;
                    UserProfile p = profileMap.get(loc.getUserId());
                    return new NearbyHit(
                            loc.getUserId(),
                            users.findById(loc.getUserId()).map(User::getUsername).orElse("?"),
                            p != null ? p.getFirstName() : null,
                            p != null ? p.getLastName() : null,
                            p != null ? p.getProfession() : null,
                            // round to 1 decimal: ~100 m precision, no exact location leak
                            Math.round(d * 10) / 10.0,
                            follows.existsByFollowerIdAndFolloweeId(loc.getUserId(), viewer.getId()));
                })
                .filter(h -> h != null)
                .sorted(Comparator.comparingDouble(NearbyHit::distanceKm))
                .limit(50)
                .toList();
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
