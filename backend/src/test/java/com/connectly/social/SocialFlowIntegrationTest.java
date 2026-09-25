package com.connectly.social;

import com.connectly.post.PostDtos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Phase 2 social layer, exercised over real HTTP on a random port:
 * registration → posts → object-level authorization → visibility rules →
 * likes → comments → follows → connection state machine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "connectly.tests.auto-activate=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocialFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    com.connectly.user.UserRepository users;

    private RestClient rest;

    String aliceToken;
    String bobToken;
    Long aliceId;
    Long bobId;
    long publicPostId;
    long followersPostId;
    long privatePostId;

    final Random rnd = new Random();

    @BeforeAll
    void setUpClient() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
    }

    // ---------- helpers ----------

    HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Register + login a fresh user; returns the bearer token. */
    String newUser(String handle) {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000_000);
        rest.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "firstName", handle, "lastName", "Test",
                        "username", (handle + suffix).toLowerCase(),
                        "email", (handle + suffix).toLowerCase() + "@example.com",
                        "password", "a-very-uncommon-passphrase-" + suffix))
                .retrieve().toBodilessEntity();

        ResponseEntity<Map> login = rest.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", (handle + suffix).toLowerCase() + "@example.com",
                        "password", "a-very-uncommon-passphrase-" + suffix))
                .retrieve().toEntity(Map.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        return (String) login.getBody().get("accessToken");
    }

    Long userId(String token) {
        ResponseEntity<Map> me = rest.get().uri("/api/v1/auth/me")
                .headers(h -> h.setBearerAuth(token)).retrieve().toEntity(Map.class);
        return ((Number) me.getBody().get("id")).longValue();
    }

    // ---------- the flow ----------

    @Test
    void fullSocialFlow() {
        // --- two users register ---
        aliceToken = newUser("Alice");
        bobToken = newUser("Bob");
        aliceId = userId(aliceToken);
        bobId = userId(bobToken);
        assertThat(aliceId).isNotEqualTo(bobId);

        // --- Alice creates one post per visibility ---
        ResponseEntity<PostDtos.PostDto> pub = rest.post().uri("/api/v1/posts")
                .headers(h -> h.setBearerAuth(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "alice public #hello", "visibility", "PUBLIC"))
                .retrieve().toEntity(PostDtos.PostDto.class);
        ResponseEntity<PostDtos.PostDto> fol = rest.post().uri("/api/v1/posts")
                .headers(h -> h.setBearerAuth(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "alice followers-only", "visibility", "FOLLOWERS"))
                .retrieve().toEntity(PostDtos.PostDto.class);
        ResponseEntity<PostDtos.PostDto> priv = rest.post().uri("/api/v1/posts")
                .headers(h -> h.setBearerAuth(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "alice secret", "visibility", "PRIVATE"))
                .retrieve().toEntity(PostDtos.PostDto.class);
        assertThat(pub.getStatusCode().value()).isEqualTo(201);
        assertThat(fol.getStatusCode().value()).isEqualTo(201);
        assertThat(priv.getStatusCode().value()).isEqualTo(201);
        publicPostId = pub.getBody().id();
        followersPostId = fol.getBody().id();
        privatePostId = priv.getBody().id();

        // --- Bob (not following) sees PUBLIC but 404 for FOLLOWERS/PRIVATE (no existence leak) ---
        assertThat(rest.get().uri("/api/v1/posts/" + publicPostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(PostDtos.PostDto.class)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(rest.get().uri("/api/v1/posts/" + followersPostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(rest.get().uri("/api/v1/posts/" + privatePostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(404);

        // --- object-level authorization: Bob cannot edit or delete Alice's post ---
        assertThat(rest.put().uri("/api/v1/posts/" + publicPostId)
                .headers(h -> h.setBearerAuth(bobToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "hacked", "visibility", "PUBLIC")).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(rest.delete().uri("/api/v1/posts/" + publicPostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(rest.get().uri("/api/v1/posts/" + publicPostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(PostDtos.PostDto.class)
                .getBody().content()).isEqualTo("alice public #hello");

        // --- likes toggle and counts move ---
        String likeUrl = "/api/v1/posts/" + publicPostId + "/like";
        assertThat(rest.post().uri(likeUrl).headers(h -> h.setBearerAuth(bobToken))
                .retrieve().toEntity(PostDtos.LikeResponse.class).getBody().liked()).isTrue();
        PostDtos.PostDto afterOn = rest.get().uri("/api/v1/posts/" + publicPostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(PostDtos.PostDto.class).getBody();
        assertThat(afterOn.likeCount()).isEqualTo(1);
        assertThat(afterOn.likedByMe()).isTrue();
        assertThat(rest.post().uri(likeUrl).headers(h -> h.setBearerAuth(bobToken))
                .retrieve().toEntity(PostDtos.LikeResponse.class).getBody().liked()).isFalse();

        // --- comments: attach, list, reject cross-post parent ---
        assertThat(rest.post().uri("/api/v1/posts/" + publicPostId + "/comments")
                .headers(h -> h.setBearerAuth(bobToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "first!")).retrieve().toEntity(PostDtos.CommentDto.class)
                .getStatusCode().value()).isEqualTo(201);
        List<PostDtos.CommentDto> comments = rest.get().uri("/api/v1/posts/" + publicPostId + "/comments")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve()
                .toEntity(new ParameterizedTypeReference<List<PostDtos.CommentDto>>() {}).getBody();
        assertThat(comments).extracting(PostDtos.CommentDto::content).containsExactly("first!");

        // --- follow unlocks FOLLOWERS posts and feeds them into Bob's home feed ---
        assertThat(rest.post().uri("/api/v1/users/" + aliceId + "/follow")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(200); // {status:"ACTIVE"}
        assertThat(rest.get().uri("/api/v1/posts/" + followersPostId)
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(PostDtos.PostDto.class)
                .getStatusCode().value()).isEqualTo(200);
        PostDtos.PostPage feed = rest.get().uri("/api/v1/posts/feed")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(PostDtos.PostPage.class).getBody();
        assertThat(feed.posts()).extracting(PostDtos.PostDto::id)
                .contains(publicPostId, followersPostId).doesNotContain(privatePostId);
        // self-follow rejected
        assertThat(rest.post().uri("/api/v1/users/" + bobId + "/follow")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(400);

        // --- connection state machine ---
        assertThat(rest.post().uri("/api/v1/connections/" + bobId)
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve().toEntity(Map.class)
                .getBody().get("status")).isEqualTo("PENDING");
        // idempotent repeat
        assertThat(rest.post().uri("/api/v1/connections/" + bobId)
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve().toEntity(Map.class)
                .getBody().get("status")).isEqualTo("PENDING");

        List<SocialService.ConnectionDto> incoming = rest.get()
                .uri(b -> b.path("/api/v1/connections").queryParam("filter", "incoming").build())
                .headers(h -> h.setBearerAuth(bobToken)).retrieve()
                .toEntity(new ParameterizedTypeReference<List<SocialService.ConnectionDto>>() {}).getBody();
        assertThat(incoming).hasSize(1);
        long requestId = incoming.get(0).id();

        // sender cannot accept their own request
        assertThat(rest.post().uri("/api/v1/connections/requests/" + requestId + "/accept")
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(403);
        // receiver accepts
        assertThat(rest.post().uri("/api/v1/connections/requests/" + requestId + "/accept")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(204);

        List<SocialService.ConnectionDto> all = rest.get().uri("/api/v1/connections")
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve()
                .toEntity(new ParameterizedTypeReference<List<SocialService.ConnectionDto>>() {}).getBody();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).status()).isEqualTo("ACCEPTED");

        // unfriend
        assertThat(rest.delete().uri("/api/v1/connections/requests/" + requestId)
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(204);
        List<SocialService.ConnectionDto> gone = rest.get().uri("/api/v1/connections")
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve()
                .toEntity(new ParameterizedTypeReference<List<SocialService.ConnectionDto>>() {}).getBody();
        assertThat(gone).isEmpty();

        // --- profile update + counts ---
        ResponseEntity<Map> me = rest.put().uri("/api/v1/users/me")
                .headers(h -> h.setBearerAuth(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("firstName", "Alice", "lastName", "Tester", "bio", "hi", "profession", "QA"))
                .retrieve().toEntity(Map.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().get("bio")).isEqualTo("hi");

        // --- anonymous: explore public, feed locked ---
        assertThat(rest.get().uri("/api/v1/posts/explore").retrieve().toEntity(PostDtos.PostPage.class)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(rest.get().uri("/api/v1/posts/feed").retrieve().toEntity(String.class)
                .getStatusCode().value()).isEqualTo(401);

        // --- search: public posts found, private content never leaks ---
        var search = rest.get().uri(b -> b.path("/api/v1/posts/search").queryParam("q", "alice public").build())
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(Map.class).getBody();
        assertThat((List<?>) search.get("posts")).isNotEmpty();
        var privateSearch = rest.get().uri(b -> b.path("/api/v1/posts/search").queryParam("q", "alice secret").build())
                .headers(h -> h.setBearerAuth(bobToken)).retrieve().toEntity(Map.class).getBody();
        assertThat((List<?>) privateSearch.get("posts")).isEmpty();
        var userSearch = rest.get().uri(b -> b.path("/api/v1/posts/search").queryParam("q", "bob").build())
                .headers(h -> h.setBearerAuth(aliceToken)).retrieve().toEntity(Map.class).getBody();
        assertThat((List<?>) userSearch.get("users")).isNotEmpty();

        // --- saved posts: toggle on, listed, toggle off, gone ---
        String saveUrl = "/api/v1/posts/" + publicPostId + "/save";
        assertThat(rest.post().uri(saveUrl).headers(h -> h.setBearerAuth(bobToken))
                .retrieve().toEntity(PostDtos.LikeResponse.class).getBody().liked()).isTrue();
        List<PostDtos.PostDto> savedList = rest.get().uri("/api/v1/posts/saved")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve()
                .toEntity(new ParameterizedTypeReference<List<PostDtos.PostDto>>() {}).getBody();
        assertThat(savedList).extracting(PostDtos.PostDto::id).containsExactly(publicPostId);
        assertThat(rest.post().uri(saveUrl).headers(h -> h.setBearerAuth(bobToken))
                .retrieve().toEntity(PostDtos.LikeResponse.class).getBody().liked()).isFalse();
        List<PostDtos.PostDto> savedEmpty = rest.get().uri("/api/v1/posts/saved")
                .headers(h -> h.setBearerAuth(bobToken)).retrieve()
                .toEntity(new ParameterizedTypeReference<List<PostDtos.PostDto>>() {}).getBody();
        assertThat(savedEmpty).isEmpty();
    }
}
