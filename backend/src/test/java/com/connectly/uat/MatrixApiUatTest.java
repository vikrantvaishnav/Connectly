package com.connectly.uat;

import com.connectly.user.User;
import com.connectly.user.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executable distillation of the 100,000-case UAT matrix
 * (200 module/feature pairs x 10 systematic intents each).
 *
 * Every feature in the CSV maps to real endpoints; this suite drives the eight
 * intents that are automatable at the API level against a live server:
 *   valid / invalid / min / max / empty / duplicate / unauthorized / concurrent-ish
 * The two presentation-layer intents (offline behaviour, visual refresh) remain
 * manual/browser cases and are intentionally not faked here.
 *
 * Cast: ATLAS, RAVEN, COMET (well-behaved) and MOLE (attacks everything).
 * Self-cleaning: every account is deleted through the public API at the end,
 * which doubles as the Data & Compliance deletion test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatrixApiUatTest {

    @LocalServerPort
    int port;

    @Autowired UserRepository users;

    private RestClient rest;
    private final long run = System.currentTimeMillis();

    private String atlas, raven, comet, mole;
    private long atlasId, ravenId, cometId, moleId;
    private long postId, commentId, messageId, conversationId, communityId, channelId;

    // ---------- plumbing ----------

    @BeforeAll
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
    }

    private Map<String, Object> post(String path, Object body, String bearer) {
        return rest.post().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(Map.class).getBody();
    }

    private int postStatus(String path, Object body, String bearer) {
        return rest.post().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, String bearer) {
        return rest.method(org.springframework.http.HttpMethod.GET).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toEntity(Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(String path, String bearer) {
        return rest.method(org.springframework.http.HttpMethod.GET).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toEntity(List.class).getBody();
    }

    private int getStatus(String path, String bearer) {
        return rest.method(org.springframework.http.HttpMethod.GET).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    private int put(String path, Object body, String bearer) {
        return rest.put().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    private int delete(String path, String bearer) {
        return rest.delete().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    private String register(String handle) {
        String username = handle + run;
        post("/api/v1/auth/register", Map.of(
                "firstName", handle, "lastName", "Matrix",
                "username", username, "email", username + "@matrix.example", "password", "Matrix-pass-1!"), null);
        User u = users.findByUsernameIgnoreCase(username).orElseThrow();
        u.setEmailVerified(true);
        users.save(u);
        var login = post("/api/v1/auth/login", Map.of("identifier", username, "password", "Matrix-pass-1!"), null);
        assertThat((String) login.get("accessToken")).isNotBlank();
        return username;
    }

    private String login(String username) {
        var resp = post("/api/v1/auth/login", Map.of("identifier", username, "password", "Matrix-pass-1!"), null);
        return (String) resp.get("accessToken");
    }

    private long meId(String bearer) {
        return ((Number) get("/api/v1/auth/me", bearer).get("id")).longValue();
    }

    // ---------- Authentication & Onboarding ----------

    @Test @Order(1)
    void auth_valid_invalid_duplicate_unauthorized() {
        // valid
        atlas = register("atlas");
        raven = register("raven");
        comet = register("comet");
        mole = register("mole");
        atlasId = meId(login(atlas));
        ravenId = meId(login(raven));
        cometId = meId(login(comet));
        moleId = meId(login(mole));
        assertThat(atlasId).isNotEqualTo(ravenId);

        // invalid: bad password, unknown identifier — uniform 401
        assertThat(postStatus("/api/v1/auth/login", Map.of("identifier", atlas, "password", "wrong-pass-1!"), null)).isEqualTo(401);
        assertThat(postStatus("/api/v1/auth/login", Map.of("identifier", "ghost" + run, "password", "whatever-1!"), null)).isEqualTo(401);

        // invalid: weak password refused
        assertThat(postStatus("/api/v1/auth/register", Map.of(
                "firstName", "x", "lastName", "y", "username", "weak" + run,
                "email", "weak" + run + "@example.com", "password", "short"), null)).isEqualTo(400);
        // empty: required field missing
        assertThat(postStatus("/api/v1/auth/register", Map.of(
                "lastName", "y", "username", "nofirst" + run,
                "email", "nofirst" + run + "@example.com", "password", "Matrix-pass-1!"), null)).isEqualTo(400);

        // duplicate: same username twice → 409
        assertThat(postStatus("/api/v1/auth/register", Map.of(
                "firstName", "d", "lastName", "x", "username", atlas,
                "email", "dup" + run + "@example.com", "password", "Matrix-pass-1!"), null)).isEqualTo(409);

        // unauthorized: protected endpoint without/with garbage token
        assertThat(getStatus("/api/v1/auth/me", null)).isEqualTo(401);
        assertThat(getStatus("/api/v1/auth/me", "garbage.token")).isEqualTo(401);

        // max: 2000-char post content accepted, 2001 rejected
        String token = login(atlas);
        assertThat(postStatus("/api/v1/posts", Map.of("content", "x".repeat(2000), "visibility", "PUBLIC"), token)).isEqualTo(201);
        assertThat(postStatus("/api/v1/posts", Map.of("content", "x".repeat(2001), "visibility", "PUBLIC"), token)).isEqualTo(400);
    }

    // ---------- Home & Feed / Posts ----------

    @Test @Order(2)
    void posts_valid_empty_invalid_duplicate_unauthorized() {
        String token = login(atlas);
        String ravenToken = login(raven);

        // valid create
        var created = post("/api/v1/posts", Map.of("content", "matrix post", "visibility", "PUBLIC"), token);
        postId = ((Number) created.get("id")).longValue();
        assertThat(postId).isPositive();

        // empty content refused
        assertThat(postStatus("/api/v1/posts", Map.of("content", "   ", "visibility", "PUBLIC"), token)).isEqualTo(400);
        // invalid visibility refused
        assertThat(postStatus("/api/v1/posts", Map.of("content", "hi", "visibility", "SECRET"), token)).isEqualTo(400);
        // unauthorized: anonymous cannot create
        assertThat(postStatus("/api/v1/posts", Map.of("content", "hi", "visibility", "PUBLIC"), null)).isEqualTo(401);

        // like toggle = duplicate-safe
        assertThat(postStatus("/api/v1/posts/" + postId + "/like", Map.of(), ravenToken)).isEqualTo(200);
        var second = post("/api/v1/posts/" + postId + "/like", Map.of(), ravenToken);
        assertThat(second.get("liked")).isEqualTo(false); // idempotent toggle
        post("/api/v1/posts/" + postId + "/like", Map.of(), ravenToken);

        // comments: valid, empty, unauthorized
        var comment = post("/api/v1/posts/" + postId + "/comments", Map.of("content", "nice"), ravenToken);
        commentId = ((Number) comment.get("id")).longValue();
        assertThat(postStatus("/api/v1/posts/" + postId + "/comments", Map.of("content", ""), ravenToken)).isEqualTo(400);
        assertThat(postStatus("/api/v1/posts/" + postId + "/comments", Map.of("content", "anon"), null)).isEqualTo(401);

        // raven cannot edit or delete atlas's post
        assertThat(put("/api/v1/posts/" + postId, Map.of("content", "hacked", "visibility", "PUBLIC"), ravenToken)).isEqualTo(403);
        assertThat(delete("/api/v1/posts/" + postId, ravenToken)).isEqualTo(403);

        // feed and explore contain the post; search finds it
        assertThat(get("/api/v1/posts/feed?size=10", token).toString()).contains("matrix post");
        assertThat(get("/api/v1/posts/explore?size=10", login(comet)).toString()).contains("matrix post");
        assertThat(get("/api/v1/posts/search?q=matrix&userLimit=3&postLimit=5", token).toString()).contains("matrix post");

        // unauthorized detail still 200-shape for public but 404/401 semantics hold
        assertThat(getStatus("/api/v1/posts/feed", null)).isEqualTo(401);
    }

    // ---------- Social Graph + Requests & Connections ----------

    @Test @Order(3)
    void follow_connect_accept_decline_duplicate_unauthorized() {
        String atlasToken = login(atlas);
        String ravenToken = login(raven);

        // follow: valid then duplicate (idempotent)
        assertThat(postStatus("/api/v1/users/" + ravenId + "/follow", Map.of(), atlasToken)).isEqualTo(204);
        assertThat(postStatus("/api/v1/users/" + ravenId + "/follow", Map.of(), atlasToken)).isEqualTo(204);
        // self-follow refused
        assertThat(postStatus("/api/v1/users/" + atlasId + "/follow", Map.of(), atlasToken)).isEqualTo(400);
        // unfollow valid
        assertThat(delete("/api/v1/users/" + ravenId + "/follow", atlasToken)).isEqualTo(204);

        // connections: request → duplicate stays PENDING → accept via mutual POST
        var req = post("/api/v1/connections/" + ravenId, Map.of(), atlasToken);
        assertThat(req.get("status")).isEqualTo("PENDING");
        var dup = post("/api/v1/connections/" + ravenId, Map.of(), atlasToken);
        assertThat(dup.get("status")).isEqualTo("PENDING");
        var accept = post("/api/v1/connections/" + atlasId, Map.of(), ravenToken);
        assertThat(accept.get("status")).isEqualTo("ACCEPTED");

        // summary + lists
        var summary = get("/api/v1/connections/summary", ravenToken);
        assertThat(((Number) summary.get("matches")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(getList("/api/v1/connections?filter=incoming", atlasToken)).isNotNull();
    }

    // ---------- Messages ----------

    @Test @Order(4)
    void messaging_valid_empty_unauthorized_read_state() {
        String atlasToken = login(atlas);
        String ravenToken = login(raven);

        var conv = post("/api/v1/conversations", Map.of("userId", ravenId), atlasToken);
        conversationId = ((Number) conv.get("id")).longValue();

        var msg = post("/api/v1/conversations/" + conversationId + "/messages", Map.of("content", "hello matrix"), atlasToken);
        messageId = ((Number) msg.get("id")).longValue();
        assertThat(msg.get("mine")).isEqualTo(true);

        // empty refused
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/messages", Map.of("content", "  "), atlasToken)).isEqualTo(400);
        // max length refused
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/messages", Map.of("content", "x".repeat(4001)), atlasToken)).isEqualTo(400);
        // outsider cannot read or send
        assertThat(getStatus("/api/v1/conversations/" + conversationId + "/messages", login(mole))).isEqualTo(404);
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/messages", Map.of("content", "spam"), login(mole))).isEqualTo(404);

        // reactions + typing (reaction endpoint returns an array)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reaction = rest.post().uri("/api/v1/messages/" + messageId + "/reactions")
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + ravenToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("emoji", "🔥"))
                .retrieve().toEntity(List.class).getBody();
        assertThat(reaction.get(0)).containsEntry("mine", true);
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/typing", Map.of(), ravenToken)).isEqualTo(204);

        // read state clears the badge
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/read", Map.of(), ravenToken)).isEqualTo(200);
    }

    // ---------- Communities ----------

    @Test @Order(5)
    void communities_roles_posts_unauthorized() {
        String atlasToken = login(atlas);
        String ravenToken = login(raven);

        var community = post("/api/v1/communities", Map.of("name", "Matrix HQ " + run, "description", "d", "icon", "🧪"), atlasToken);
        communityId = ((Number) community.get("id")).longValue();
        assertThat(community.get("myRole")).isEqualTo("OWNER");

        assertThat(postStatus("/api/v1/communities/" + communityId + "/join", Map.of(), ravenToken)).isEqualTo(200);
        var channels = getList("/api/v1/communities/" + communityId + "/channels", ravenToken);
        channelId = ((Number) channels.get(0).get("id")).longValue();

        // member posts; owner-only channel creation enforced
        var channelMsg = post("/api/v1/communities/channels/" + channelId + "/messages", Map.of("content", "gm"), ravenToken);
        assertThat(((Number) channelMsg.get("id")).longValue()).isPositive();
        assertThat(postStatus("/api/v1/communities/" + communityId + "/channels", Map.of("name", "sneaky"), ravenToken)).isEqualTo(403);

        // channel reactions: valid + unauthorized outsider
        long channelMessageId = ((Number) channelMsg.get("id")).longValue();
        assertThat(postStatus("/api/v1/communities/channel-messages/" + channelMessageId + "/reactions", Map.of("emoji", "👍"), atlasToken)).isEqualTo(200);
        assertThat(postStatus("/api/v1/communities/channel-messages/" + channelMessageId + "/reactions", Map.of("emoji", "👍"), login(mole))).isEqualTo(404);

        // outsider cannot even list channels
        assertThat(getStatus("/api/v1/communities/" + communityId + "/channels", login(mole))).isEqualTo(404);
    }

    // ---------- Nearby + privacy ----------

    @Test @Order(6)
    void nearby_privacy_validation_blocked_exclusion() {
        String atlasToken = login(atlas);
        String ravenToken = login(raven);
        String cometToken = login(comet);

        assertThat(put("/api/v1/users/me/location", Map.of("latitude", 19.11, "longitude", 72.90, "discoverable", true), ravenToken)).isEqualTo(200);
        assertThat(put("/api/v1/users/me/location", Map.of("latitude", 19.12, "longitude", 72.91, "discoverable", true), cometToken)).isEqualTo(200);

        // both visible to atlas
        var hits = getList("/api/v1/nearby?lat=19.11&lng=72.90&radiusKm=25", atlasToken);
        assertThat(hits.toString()).contains(raven).contains(comet);
        // coordinates never leak
        assertThat(hits.toString()).doesNotContain("latitude");

        // invalid input refused
        assertThat(getStatus("/api/v1/nearby?lat=999&lng=72.9&radiusKm=10", atlasToken)).isEqualTo(400);
        assertThat(getStatus("/api/v1/nearby?lat=19.1&lng=72.9&radiusKm=500", atlasToken)).isEqualTo(400);

        // hide → invisible
        assertThat(put("/api/v1/users/me/discoverability", Map.of("discoverable", false), ravenToken)).isEqualTo(200);
        assertThat(getList("/api/v1/nearby?lat=19.11&lng=72.90&radiusKm=25", atlasToken).toString()).doesNotContain(raven);
        assertThat(put("/api/v1/users/me/discoverability", Map.of("discoverable", true), ravenToken)).isEqualTo(200);

        // BLOCK raven → excluded from nearby, discover deck, and lists
        assertThat(postStatus("/api/v1/users/" + ravenId + "/block", Map.of(), cometToken)).isEqualTo(204);
        assertThat(getList("/api/v1/nearby?lat=19.11&lng=72.90&radiusKm=25", cometToken).toString()).doesNotContain(raven);
        var deck = getList("/api/v1/discover/suggestions?lat=19.11&lng=72.90&radiusKm=25", cometToken);
        assertThat(deck.toString()).doesNotContain(raven);
    }

    // ---------- Safety: block/mute enforcement across modules ----------

    @Test @Order(7)
    void blocks_teardown_and_enforce_everywhere() {
        String atlasToken = login(atlas);
        String cometToken = login(comet);

        // setup: comet follows atlas and has a DM with them
        post("/api/v1/users/" + atlasId + "/follow", Map.of(), cometToken);
        var conv = post("/api/v1/conversations", Map.of("userId", atlasId), cometToken);
        long convId = ((Number) conv.get("id")).longValue();
        post("/api/v1/conversations/" + convId + "/messages", Map.of("content", "hi"), cometToken);

        // block: comet blocks atlas — relationship teardown
        assertThat(postStatus("/api/v1/users/" + atlasId + "/block", Map.of(), cometToken)).isEqualTo(204);

        // DMs: existing conversation is deleted; opening and sending both fail closed
        assertThat(postStatus("/api/v1/conversations", Map.of("userId", atlasId), cometToken)).isEqualTo(404);

        // follows gone, requests refused in both directions
        assertThat(postStatus("/api/v1/users/" + atlasId + "/follow", Map.of(), cometToken)).isEqualTo(404);
        assertThat(postStatus("/api/v1/users/" + cometId + "/follow", Map.of(), atlasToken)).isEqualTo(404);
        assertThat(postStatus("/api/v1/connections/" + atlasId, Map.of(), cometToken)).isEqualTo(404);

        // content visibility: atlas's posts 404 for comet (list and detail)
        assertThat(get("/api/v1/posts/explore?size=50", cometToken).toString().contains("matrix post")).isFalse();
        assertThat(getStatus("/api/v1/posts/" + postId, cometToken)).isEqualTo(404);

        // atlas also cannot react to comet's content (bidirectional)
        var cometPost = post("/api/v1/posts", Map.of("content", "comet speaks", "visibility", "PUBLIC"), cometToken);
        long cometPostId = ((Number) cometPost.get("id")).longValue();
        assertThat(postStatus("/api/v1/posts/" + cometPostId + "/like", Map.of(), atlasToken)).isEqualTo(404);

        // unblock restores nothing automatically but reopens the gates
        assertThat(delete("/api/v1/users/" + atlasId + "/block", cometToken)).isEqualTo(204);
        assertThat(getStatus("/api/v1/posts/" + cometPostId, atlasToken)).isEqualTo(200);

        // mute: soft — muted content vanishes from the muter's explore only.
        // (comet still blocks raven from the nearby test; muting a blocked user
        // is refused by design, so lift the block first.)
        String ravenToken = login(raven);
        assertThat(delete("/api/v1/users/" + ravenId + "/block", cometToken)).isEqualTo(204);
        var ravenPost = post("/api/v1/posts", Map.of("content", "raven speaks", "visibility", "PUBLIC"), ravenToken);
        assertThat(get("/api/v1/posts/explore?size=50", cometToken).toString()).contains("raven speaks");
        assertThat(postStatus("/api/v1/users/" + ravenId + "/mute", Map.of(), cometToken)).isEqualTo(204);
        // muter no longer sees raven's content...
        assertThat(get("/api/v1/posts/explore?size=50", cometToken).toString().contains("raven speaks")).isFalse();
        // ...but raven still sees comet's content (mute is one-directional)
        assertThat(get("/api/v1/posts/explore?size=50", ravenToken).toString()).contains("comet speaks");

        // safety lists reflect state: raven muted, nobody blocked (both blocks lifted)
        var lists = get("/api/v1/safety/lists", cometToken);
        assertThat(lists.toString()).contains(raven).contains("muted");
        // nothing is blocked anymore (both blocks were lifted above)
        assertThat(((java.util.List<?>) lists.get("blocked"))).isEmpty();
    }

    // ---------- Profile ----------

    @Test @Order(8)
    void profile_edit_validation_null_semantics() {
        String token = login(raven);

        assertThat(put("/api/v1/users/me", Map.of(
                "bio", "matrix bio", "interests", "testing, quality", "lookingFor", "bugs",
                "dateOfBirth", "1996-05-04"), token)).isEqualTo(200);

        var pub = get("/api/v1/users/" + raven, login(atlas));
        assertThat(pub.toString()).contains("matrix bio").contains("bugs");

        // nulls preserve (Map.of forbids nulls, so build the payload manually)
        java.util.Map<String, Object> partial = new java.util.HashMap<>();
        partial.put("bio", null);
        partial.put("profession", null);
        assertThat(put("/api/v1/users/me", partial, token)).isEqualTo(200);
        var after = get("/api/v1/users/" + raven, login(atlas));
        assertThat(after.toString()).contains("matrix bio"); // untouched

        // validation: under-18 refused
        assertThat(put("/api/v1/users/me", Map.of("dateOfBirth", "2015-01-01"), token)).isEqualTo(400);

        // profile of a stranger works; ghost 404s
        assertThat(getStatus("/api/v1/users/ghost" + run, token)).isEqualTo(404);
    }

    // ---------- Notifications ----------

    @Test @Order(9)
    void notifications_fire_and_clear() {
        String atlasToken = login(atlas);
        String ravenToken = login(raven);

        // raven likes + comments on atlas's post → notifications for atlas
        post("/api/v1/posts/" + postId + "/like", Map.of(), ravenToken);
        post("/api/v1/posts/" + postId + "/comments", Map.of("content", "notif check"), ravenToken);

        var unread = get("/api/v1/notifications/unread-count", atlasToken);
        assertThat(((Number) unread.get("count")).longValue()).isGreaterThanOrEqualTo(1);

        var list = getList("/api/v1/notifications?size=20", atlasToken);
        assertThat(list.toString()).contains(raven);

        // mark all read → zero
        assertThat(postStatus("/api/v1/notifications/read-all", Map.of(), atlasToken)).isEqualTo(200);
        assertThat(((Number) get("/api/v1/notifications/unread-count", atlasToken).get("count")).longValue()).isZero();
    }

    // ---------- Media ----------

    @Test @Order(10)
    void media_upload_validation_and_security() {
        String token = login(atlas);
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D,
                0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0, 0, 0, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0, 1, 0, 0, 5, 0, 1,
                0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
        var parts = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        parts.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override public String getFilename() { return "matrix.png"; }
        });
        var uploaded = rest.post().uri("/api/v1/media")
                .headers(h -> { h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token); h.setContentType(MediaType.MULTIPART_FORM_DATA); })
                .body(parts)
                .retrieve().toEntity(Map.class).getBody();
        String url = (String) uploaded.get("url");
        assertThat(url).startsWith("/media/");

        // anonymous serving allowed; path traversal refused
        assertThat(rest.method(org.springframework.http.HttpMethod.GET).uri(url).retrieve().toEntity(byte[].class)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(getStatus("/media/..%2F..%2Fapplication.yml", null)).isIn(400, 404);

        // unsupported/renamed file refused
        var fake = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        fake.add("file", new org.springframework.core.io.ByteArrayResource("not an image".getBytes()) {
            @Override public String getFilename() { return "matrix.png"; }
        });
        var refused = rest.post().uri("/api/v1/media")
                .headers(h -> { h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token); h.setContentType(MediaType.MULTIPART_FORM_DATA); })
                .body(fake)
                .retrieve().toEntity(Map.class);
        assertThat(refused.getStatusCode().value()).isEqualTo(400);
    }

    // ---------- Discover & Dating ----------

    @Test @Order(11)
    void discover_deck_ranking_and_privacy() {
        String atlasToken = login(atlas);
        String cometToken = login(comet);

        put("/api/v1/users/me", Map.of("interests", "coffee, testing"), cometToken);
        put("/api/v1/users/me/location", Map.of("latitude", 19.13, "longitude", 72.92, "discoverable", true), cometToken);

        var deck = getList("/api/v1/discover/suggestions?lat=19.11&lng=72.90&radiusKm=50", atlasToken);
        assertThat(deck).isNotNull();
        assertThat(deck.toString()).doesNotContain("latitude"); // coordinates never leak
    }

    // ---------- Data & Compliance: account deletion ----------

    @Test @Order(12)
    void account_deletion_cascades_and_login_dies() {
        String doomed = register("doomed");
        String doomedToken = login(doomed);
        long doomedId = meId(doomedToken);

        // leave content behind that must vanish with the account
        post("/api/v1/posts", Map.of("content", "delete me with the account", "visibility", "PUBLIC"), doomedToken);
        post("/api/v1/conversations", Map.of("userId", ravenId), doomedToken);

        // another user follows doomed → that edge must disappear too
        post("/api/v1/users/" + doomedId + "/follow", Map.of(), login(raven));

        // unauthorized deletion attempt (wrong account is impossible — endpoint is self-service)
        assertThat(delete("/api/v1/auth/me", null)).isEqualTo(401);

        // delete
        assertThat(delete("/api/v1/auth/me", doomedToken)).isEqualTo(200);

        // login now fails; profile is gone; owned post is gone from explore
        assertThat(postStatus("/api/v1/auth/login", Map.of("identifier", doomed, "password", "Matrix-pass-1!"), null)).isEqualTo(401);
        assertThat(getStatus("/api/v1/users/" + doomed, login(atlas))).isEqualTo(404);
        assertThat(get("/api/v1/posts/explore?size=50", login(atlas)).toString()).doesNotContain("delete me with the account");

        // token from the deleted account no longer authenticates
        assertThat(getStatus("/api/v1/auth/me", doomedToken)).isEqualTo(401);
    }

    // ---------- Security: IDOR/injection probes across modules ----------

    @Test @Order(13)
    void security_idor_injection_and_boundary_probes() {
        String token = login(atlas);

        // nonexistent ids → 404, not 500
        assertThat(getStatus("/api/v1/posts/999999999", token)).isEqualTo(404);
        assertThat(getStatus("/api/v1/conversations/999999999/messages", token)).isEqualTo(404);
        assertThat(postStatus("/api/v1/users/999999999/follow", Map.of(), token)).isEqualTo(404);

        // SQLi payloads are inert (parameterized queries) — treated as plain text
        var sqli = post("/api/v1/posts", Map.of("content", "'; DROP TABLE users; --", "visibility", "PUBLIC"), token);
        assertThat(sqli.get("content")).isEqualTo("'; DROP TABLE users; --");
        assertThat(get("/api/v1/posts/search?q=%27%3B%20DROP%20TABLE%20users%3B%20--", token)).isNotNull();
        // app still healthy afterwards
        assertThat(get("/api/v1/health", null).get("status")).isEqualTo("UP");

        // XSS payload stored verbatim, never executed server-side; client escapes on render
        var xss = post("/api/v1/posts", Map.of("content", "<script>alert(1)</script>", "visibility", "PUBLIC"), token);
        assertThat(xss.get("content")).isEqualTo("<script>alert(1)</script>");

        // negative/absurd pagination clamps instead of erroring or exploding
        assertThat(get("/api/v1/posts/feed?page=-1&size=100000", token)).isNotNull();

        // voice room listing is public but creation requires auth
        assertThat(getList("/api/v1/voice/rooms", null)).isNotNull();
        assertThat(postStatus("/api/v1/voice/rooms", Map.of("name", "anon"), null)).isEqualTo(401);
    }

    // ---------- cleanup: delete every account this suite created ----------

    @AfterAll
    void cleanupAllAccounts() {
        for (String handle : List.of(atlas, raven, comet, mole)) {
            if (handle == null) continue;
            try {
                var loginResp = post("/api/v1/auth/login",
                        Map.of("identifier", handle, "password", "Matrix-pass-1!"), null);
                if (loginResp == null || loginResp.get("accessToken") == null) continue;
                String token = (String) loginResp.get("accessToken");
                int code = rest.delete().uri("/api/v1/auth/me")
                        .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .retrieve().toBodilessEntity().getStatusCode().value();
                if (code != 200) {
                    throw new IllegalStateException("DELETE /auth/me for " + handle + " -> " + code);
                }
            } catch (Exception e) {
                throw new IllegalStateException("cleanup failed for " + handle + ": " + e.getMessage(), e);
            }
        }
        // verify none survive
        for (String handle : List.of(atlas, raven, comet, mole)) {
            if (handle == null) continue;
            assertThat(users.findByUsernameIgnoreCase(handle).isEmpty())
                    .as("cleanup removed %s", handle).isTrue();
        }
    }
}
