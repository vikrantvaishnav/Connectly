package com.connectly.nearby;

import com.connectly.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NearbyController {

    private final NearbyService nearby;

    public NearbyController(NearbyService nearby) {
        this.nearby = nearby;
    }

    public record UpdateLocationRequest(Double latitude, Double longitude, boolean discoverable) {}

    public record DiscoverabilityRequest(boolean discoverable) {}

    @PutMapping("/users/me/location")
    public NearbyService.NearbyStatus updateLocation(@AuthenticationPrincipal User me,
                                                     @RequestBody UpdateLocationRequest req) {
        nearby.updateLocation(me, req.latitude(), req.longitude(), req.discoverable());
        return nearby.status(me);
    }

    @PutMapping("/users/me/discoverability")
    public NearbyService.NearbyStatus setDiscoverable(@AuthenticationPrincipal User me,
                                                      @RequestBody DiscoverabilityRequest req) {
        nearby.setDiscoverable(me, req.discoverable());
        return nearby.status(me);
    }

    @GetMapping("/users/me/location-status")
    public NearbyService.NearbyStatus status(@AuthenticationPrincipal User me) {
        return nearby.status(me);
    }

    @GetMapping("/nearby")
    public List<NearbyService.NearbyHit> nearby(@AuthenticationPrincipal User me,
                                                double lat,
                                                double lng,
                                                double radiusKm) {
        validate(lat, lng, radiusKm);
        return nearby.findNearby(me, lat, lng, radiusKm);
    }

    /** Dating-style discovery deck — ranked people nearby, already-connected people excluded. */
    @GetMapping("/discover/suggestions")
    public List<NearbyService.DiscoverCard> suggestions(@AuthenticationPrincipal User me,
                                                        double lat,
                                                        double lng,
                                                        @RequestParam(defaultValue = "50") double radiusKm) {
        validate(lat, lng, radiusKm);
        return nearby.suggestions(me, lat, lng, radiusKm);
    }

    private static void validate(double lat, double lng, double radiusKm) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw com.connectly.common.error.ApiException.badRequest("Coordinates out of range");
        }
        if (radiusKm < 1 || radiusKm > 50) {
            throw com.connectly.common.error.ApiException.badRequest("Radius must be between 1 and 50 km");
        }
    }
}
