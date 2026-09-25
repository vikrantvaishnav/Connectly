package com.connectly.voice;

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
 * Phase 7: voice room lifecycle — create/join/mute/leave/close with
 * host-only close and participant-sync guarantees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "connectly.tests.auto-activate=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VoiceRoomIntegrationTest {

    @LocalServerPort
    int port;

    private RestClient rest;
    String hostToken;
    String guestToken;
    String strangerToken;

    final Random rnd = new Random();

    @BeforeAll
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
        String s = String.valueOf(rnd.nextInt(1_000_000));
        hostToken = newUser("vhost" + s);
        guestToken = newUser("vguest" + s);
        strangerToken = newUser("vstr" + s);
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
                .body(Map.of("firstName", "V", "lastName", "User",
                        "username", handle, "email", handle + "@example.com",
                        "password", "Str0ng-Passphrase-9x!"))
                .retrieve().toBodilessEntity();
        ResponseEntity<Map> login = rest.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", handle + "@example.com", "password", "Str0ng-Passphrase-9x!"))
                .retrieve().toEntity(Map.class);
        return (String) login.getBody().get("accessToken");
    }

    @Test
    void voiceRoomFlow() {
        // --- create ---
        var room = rest.post().uri("/api/v1/voice/rooms")
                .headers(h -> h.addAll(auth(hostToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Friday hangout"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        long roomId = ((Number) room.get("id")).longValue();
        assertThat(room.get("status")).isEqualTo("OPEN");
        assertThat(((Number) room.get("participantCount")).intValue()).isZero();

        // --- guests join; peer list grows and includes names ---
        var peers1 = rest.post().uri("/api/v1/voice/rooms/" + roomId + "/join")
                .headers(h -> h.addAll(auth(hostToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(peers1).hasSize(1);

        rest.post().uri("/api/v1/voice/rooms/" + roomId + "/join")
                .headers(h -> h.addAll(auth(guestToken)))
                .retrieve().toBodilessEntity();

        var peers2 = rest.get().uri("/api/v1/voice/rooms/" + roomId + "/peers")
                .headers(h -> h.addAll(auth(guestToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(peers2).hasSize(2);

        // --- stranger (not in room) cannot see peers: 404 ---
        int strangerPeers = rest.get().uri("/api/v1/voice/rooms/" + roomId + "/peers")
                .headers(h -> h.addAll(auth(strangerToken)))
                .retrieve().toEntity(String.class).getStatusCode().value();
        assertThat(strangerPeers).isEqualTo(404);

        // --- mute myself; peers see it ---
        var peers3 = rest.post().uri("/api/v1/voice/rooms/" + roomId + "/mute")
                .headers(h -> h.addAll(auth(guestToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("muted", true))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(peers3.stream().filter(p -> ((Boolean) p.get("muted"))).count()).isEqualTo(1);

        // --- guest leaves; host remains ---
        rest.post().uri("/api/v1/voice/rooms/" + roomId + "/leave")
                .headers(h -> h.addAll(auth(guestToken)))
                .retrieve().toBodilessEntity();
        var peers4 = rest.post().uri("/api/v1/voice/rooms/" + roomId + "/join")
                .headers(h -> h.addAll(auth(hostToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(peers4).hasSize(1);

        // --- stranger cannot close the host's room ---
        int strangerClose = rest.post().uri("/api/v1/voice/rooms/" + roomId + "/close")
                .headers(h -> h.addAll(auth(strangerToken)))
                .retrieve().toEntity(String.class).getStatusCode().value();
        assertThat(strangerClose).isEqualTo(403);

        // --- host closes; room disappears from open list ---
        rest.post().uri("/api/v1/voice/rooms/" + roomId + "/close")
                .headers(h -> h.addAll(auth(hostToken)))
                .retrieve().toBodilessEntity();
        var open = rest.get().uri("/api/v1/voice/rooms")
                .headers(h -> h.setBearerAuth(hostToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(open.stream().noneMatch(r -> ((Number) r.get("id")).longValue() == roomId)).isTrue();

        // --- joining a closed room fails ---
        int closedJoin = rest.post().uri("/api/v1/voice/rooms/" + roomId + "/join")
                .headers(h -> h.addAll(auth(guestToken)))
                .retrieve().toEntity(String.class).getStatusCode().value();
        assertThat(closedJoin).isEqualTo(400);
    }
}
