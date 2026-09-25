package com.connectly.community;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6: community lifecycle, membership roles, channels, channel messaging,
 * and access control (non-members get 404 on member-only surfaces).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "connectly.tests.auto-activate=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommunityFlowIntegrationTest {

    @LocalServerPort
    int port;

    private RestClient rest;
    String ownerToken;
    String memberToken;
    String outsiderToken;
    long ownerId;
    long communityId;

    final Random rnd = new Random();

    @BeforeAll
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
        String s = String.valueOf(rnd.nextInt(1_000_000));
        ownerToken = newUser("cown" + s);
        memberToken = newUser("cmem" + s);
        outsiderToken = newUser("cout" + s);
        ownerId = meId(ownerToken);

        var created = rest.post().uri("/api/v1/communities")
                .headers(h -> h.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Mumbai Tech Hub " + s, "description", "All things tech", "icon", "💻"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        communityId = ((Number) created.get("id")).longValue();
    }

    HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    String newUser(String handle) {
        rest.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("firstName", "C", "lastName", "User",
                        "username", handle, "email", handle + "@example.com",
                        "password", "Str0ng-Passphrase-9x!"))
                .retrieve().toBodilessEntity();
        ResponseEntity<Map> login = rest.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", handle + "@example.com", "password", "Str0ng-Passphrase-9x!"))
                .retrieve().toEntity(Map.class);
        return (String) login.getBody().get("accessToken");
    }

    Long meId(String token) {
        Map<String, Object> me = rest.get().uri("/api/v1/auth/me")
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return ((Number) me.get("id")).longValue();
    }

    @Test
    void communityFlow() {
        // --- defaults created: general + announcements ---
        var chans = rest.get().uri("/api/v1/communities/" + communityId + "/channels")
                .headers(h -> h.setBearerAuth(ownerToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(chans).hasSize(2);
        assertThat(chans.get(0).get("name")).isEqualTo("general");

        // --- owner appears in member list with OWNER role ---
        var members = rest.get().uri("/api/v1/communities/" + communityId + "/members")
                .headers(h -> h.setBearerAuth(ownerToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("role")).isEqualTo("OWNER");

        // --- outsider cannot read channels or messages (404, no existence leak) ---
        int outsiderChans = rest.get().uri("/api/v1/communities/" + communityId + "/channels")
                .headers(h -> h.setBearerAuth(outsiderToken))
                .retrieve().toEntity(String.class).getStatusCode().value();
        assertThat(outsiderChans).isEqualTo(404);

        // --- member joins and posts in #general ---
        rest.post().uri("/api/v1/communities/" + communityId + "/join")
                .headers(h -> h.setBearerAuth(memberToken))
                .retrieve().toBodilessEntity();
        long generalId = ((Number) chans.get(0).get("id")).longValue();
        var msg = rest.post().uri("/api/v1/communities/channels/" + generalId + "/messages")
                .headers(h -> h.setBearerAuth(memberToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "Hello from the community!"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(msg.get("content")).isEqualTo("Hello from the community!");
        assertThat((Boolean) msg.get("mine")).isTrue();

        // --- member reads history (newest first) ---
        var history = rest.get().uri("/api/v1/communities/channels/" + generalId + "/messages")
                .headers(h -> h.setBearerAuth(memberToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(history).hasSize(1);

        // --- MEMBER cannot create channels (role gate 403) ---
        int denied = rest.post().uri("/api/v1/communities/" + communityId + "/channels")
                .headers(h -> h.setBearerAuth(memberToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "memes", "topic", "fun"))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(denied).isEqualTo(403);

        // --- owner creates a channel; member sees 3 ---
        rest.post().uri("/api/v1/communities/" + communityId + "/channels")
                .headers(h -> h.setBearerAuth(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "memes", "topic", "fun stuff"))
                .retrieve().toBodilessEntity();
        var chans2 = rest.get().uri("/api/v1/communities/" + communityId + "/channels")
                .headers(h -> h.setBearerAuth(memberToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(chans2).hasSize(3);

        // --- member leaves; loses access again ---
        rest.delete().uri("/api/v1/communities/" + communityId + "/join")
                .headers(h -> h.setBearerAuth(memberToken))
                .retrieve().toBodilessEntity();
        int afterLeave = rest.get().uri("/api/v1/communities/" + communityId + "/channels")
                .headers(h -> h.setBearerAuth(memberToken))
                .retrieve().toEntity(String.class).getStatusCode().value();
        assertThat(afterLeave).isEqualTo(404);

        // --- owner cannot leave own community ---
        int ownerLeave = rest.delete().uri("/api/v1/communities/" + communityId + "/join")
                .headers(h -> h.setBearerAuth(ownerToken))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(ownerLeave).isEqualTo(400);
    }
}
