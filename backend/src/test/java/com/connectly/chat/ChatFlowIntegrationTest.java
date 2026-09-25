package com.connectly.chat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 chat layer over real HTTP: conversation get-or-create,
 * send/receive, unread counts, read receipts, and object-level
 * authorization (strangers get 404, never 403).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "connectly.tests.auto-activate=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatFlowIntegrationTest {

    @LocalServerPort
    int port;

    private RestClient rest;

    String aliceToken;
    String bobToken;
    String malloryToken;
    Long aliceId;
    Long bobId;

    final Random rnd = new Random();

    @BeforeAll
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
        String suffix = String.valueOf(rnd.nextInt(1_000_000));
        aliceToken = newUser("alice" + suffix);
        bobToken = newUser("bob" + suffix);
        malloryToken = newUser("mallory" + suffix);
        aliceId = meId(aliceToken);
        bobId = meId(bobToken);
    }

    // ---------- helpers ----------

    HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    String newUser(String handle) {
        rest.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", handle + "@chat.test",
                        "username", handle,
                        "password", "Str0ng-Passphrase-9x!",
                        "firstName", "Test", "lastName", "User"))
                .retrieve().toBodilessEntity();
        // tests run with mail mode log against H2; verification is not required for login
        var login = rest.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", handle + "@chat.test", "password", "Str0ng-Passphrase-9x!"))
                .retrieve()
                .body(Map.class);
        assertThat(login).isNotNull();
        return (String) login.get("accessToken");
    }

    Long meId(String token) {
        Map<String, Object> me = rest.get().uri("/api/v1/auth/me")
                .headers(h -> h.addAll(auth(token)))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return ((Number) me.get("id")).longValue();
    }

    // ---------- tests ----------

    @Test
    void chatFlow() {
        // --- open (create) the conversation ---
        var open = rest.post().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(aliceToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", bobId))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(open).isNotNull();
        Long convId = ((Number) open.get("id")).longValue();
        assertThat(((Number) open.get("otherUserId")).longValue()).isEqualTo(bobId);

        // --- idempotent: opening again returns the same conversation ---
        var again = rest.post().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(bobToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", aliceId))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(((Number) again.get("id")).longValue()).isEqualTo(convId);

        // --- cannot chat with yourself ---
        int selfStatus = rest.post().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(aliceToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", aliceId))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(selfStatus).isEqualTo(400);

        // --- send messages both ways ---
        var m1 = rest.post().uri("/api/v1/conversations/" + convId + "/messages")
                .headers(h -> h.addAll(auth(aliceToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "Hey Bob!"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(m1.get("content")).isEqualTo("Hey Bob!");
        assertThat((Boolean) m1.get("mine")).isTrue();

        var m2 = rest.post().uri("/api/v1/conversations/" + convId + "/messages")
                .headers(h -> h.addAll(auth(bobToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "Hi Alice!"))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(m2.get("content")).isEqualTo("Hi Alice!");

        // --- unread: replying marks you read, so bob (who replied last) has 0;
        // alice has 1 unread (bob's reply) ---
        var bobInbox = rest.get().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(bobToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(bobInbox).hasSize(1);
        assertThat(((Number) bobInbox.get(0).get("unread")).longValue()).isZero();

        var aliceInbox = rest.get().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(aliceToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        long aliceUnread = ((Number) aliceInbox.get(0).get("unread")).longValue();
        assertThat(aliceUnread).isEqualTo(1);

        // --- alice reads; her unread drops to 0 ---
        rest.post().uri("/api/v1/conversations/" + convId + "/read")
                .headers(h -> h.addAll(auth(aliceToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("upToMessageId", ((Number) m2.get("id")).longValue()))
                .retrieve().toBodilessEntity();
        var aliceInbox2 = rest.get().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(aliceToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(((Number) aliceInbox2.get(0).get("unread")).longValue()).isZero();

        // --- object-level authorization: mallory gets 404 on everything ---
        int malloryRead = rest.get().uri("/api/v1/conversations/" + convId + "/messages")
                .headers(h -> h.addAll(auth(malloryToken)))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(malloryRead).isEqualTo(404);

        int mallorySend = rest.post().uri("/api/v1/conversations/" + convId + "/messages")
                .headers(h -> h.addAll(auth(malloryToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "let me in"))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(mallorySend).isEqualTo(404);

        int malloryOpenFromFakeId = rest.post().uri("/api/v1/conversations")
                .headers(h -> h.addAll(auth(malloryToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", 424242))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(malloryOpenFromFakeId).isEqualTo(404);

        // --- empty message rejected ---
        int emptyStatus = rest.post().uri("/api/v1/conversations/" + convId + "/messages")
                .headers(h -> h.addAll(auth(aliceToken)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "   "))
                .retrieve().toEntity(Map.class).getStatusCode().value();
        assertThat(emptyStatus).isEqualTo(400);

        // --- history order and 'mine' flags ---
        var history = rest.get()
                .uri("/api/v1/conversations/" + convId + "/messages?size=10")
                .headers(h -> h.addAll(auth(aliceToken)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("content")).isEqualTo("Hi Alice!"); // newest first
        assertThat((Boolean) history.get(0).get("mine")).isFalse();       // bob's message
        assertThat((Boolean) history.get(1).get("mine")).isTrue();
    }
}

