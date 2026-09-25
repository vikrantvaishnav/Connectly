package com.connectly.nearby;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Phase 3 nearby discovery over real HTTP. The privacy invariants are the point:
 * hidden users never appear, coordinates never leave the server, radius is clamped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "connectly.tests.auto-activate=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NearbyFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    com.connectly.user.UserRepository users;

    private RestClient rest;

    @BeforeAll
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
    }

    private record TestUser(String token, long id, String username) {}

    private TestUser newUser(String handle) {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000_000);
        rest.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("firstName", handle, "lastName", "Near",
                        "username", (handle + suffix).toLowerCase(),
                        "email", (handle + suffix).toLowerCase() + "@example.com",
                        "password", "another-uncommon-pass-" + suffix))
                .retrieve().toBodilessEntity();
        ResponseEntity<Map> login = rest.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", (handle + suffix).toLowerCase() + "@example.com",
                        "password", "another-uncommon-pass-" + suffix))
                .retrieve().toEntity(Map.class);
        String token = (String) login.getBody().get("accessToken");
        long id = ((Number) ((Map<?, ?>) login.getBody().get("user")).get("id")).longValue();
        return new TestUser(token, id, (String) ((Map<?, ?>) login.getBody().get("user")).get("username"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nearbyPrivacyFlow() {
        TestUser me = newUser("Me");
        TestUser near = newUser("Near");
        TestUser far = newUser("Far");
        TestUser hidden = newUser("Hidden");

        // Mumbai coordinates: near is 2-3 km away, far is ~25 km away, hidden shares but opts out
        share(me, 19.0760, 72.8777, true);
        share(near, 19.0550, 72.9000, true);   // ~3.5 km
        share(far, 18.9400, 72.8350, true);    // ~16 km
        share(hidden, 19.0761, 72.8778, false); // metres away but discoverable=false

        // --- discoverable=false users are invisible even at close range; 20 km radius catches near (3.3 km) + far (15.8 km) ---
        List<Map<String, Object>> hits = nearby(me, 20);
        List<String> names = hits.stream().map(h -> (String) h.get("username")).toList();
        assertThat(names).contains(near.username(), far.username()).doesNotContain(hidden.username());

        // --- coordinates never appear in any response field ---
        for (Map<String, Object> hit : hits) {
            assertThat(hit.keySet()).noneMatch(k -> k.toLowerCase().contains("lat") || k.toLowerCase().contains("lng") || k.toLowerCase().contains("coord"));
        }

        // --- distance present, sorted ascending, rounded ---
        List<Double> distances = hits.stream().map(h -> ((Number) h.get("distanceKm")).doubleValue()).toList();
        assertThat(distances).isSorted();
        assertThat(distances.get(0)).isCloseTo(3.5, within(1.5));

        // --- small radius excludes the far user ---
        List<Map<String, Object>> tight = nearby(me, 5);
        assertThat(tight).extracting(h -> h.get("username")).containsExactly(near.username());

        // --- radius is clamped server-side (validation rejects 999) ---
        ResponseEntity<String> absurd = rest.get()
                .uri(b -> b.path("/api/v1/nearby")
                        .queryParam("lat", 19.076).queryParam("lng", 72.8777).queryParam("radiusKm", 999).build())
                .headers(h -> h.setBearerAuth(me.token())).retrieve().toEntity(String.class);
        assertThat(absurd.getStatusCode().value()).isEqualTo(400);

        // --- going hidden removes you from everyone else's results ---
        rest.put().uri("/api/v1/users/me/discoverability")
                .headers(h -> h.setBearerAuth(near.token())).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("discoverable", false)).retrieve().toBodilessEntity();
        List<Map<String, Object>> afterHide = nearby(me, 20);
        assertThat(afterHide).extracting(h -> h.get("username")).doesNotContain(near.username());

        // --- status endpoint reflects state ---
        ResponseEntity<Map> status = rest.get().uri("/api/v1/users/me/location-status")
                .headers(h -> h.setBearerAuth(me.token())).retrieve().toEntity(Map.class);
        assertThat(status.getBody().get("locationShared")).isEqualTo(true);
        assertThat(status.getBody().get("discoverable")).isEqualTo(true);
    }

    private void share(TestUser u, double lat, double lng, boolean discoverable) {
        ResponseEntity<Map> res = rest.put().uri("/api/v1/users/me/location")
                .headers(h -> h.setBearerAuth(u.token())).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("latitude", lat, "longitude", lng, "discoverable", discoverable))
                .retrieve().toEntity(Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    private List<Map<String, Object>> nearby(TestUser u, double radiusKm) {
        return rest.get()
                .uri(b -> b.path("/api/v1/nearby")
                        .queryParam("lat", 19.076).queryParam("lng", 72.8777).queryParam("radiusKm", radiusKm).build())
                .headers(h -> h.setBearerAuth(u.token())).retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .getBody();
    }
}
