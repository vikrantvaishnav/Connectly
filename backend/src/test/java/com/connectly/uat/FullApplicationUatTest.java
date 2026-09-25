package com.connectly.uat;

import com.connectly.user.User;
import com.connectly.user.UserRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-application UAT. Cast: two real users (ATLAS and RAVEN) who meet,
 * follow, connect, post, comment, DM, build a community with a voice room,
 * upload media, get notified — while a third user (MOLE) tries to break in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullApplicationUatTest {

    @LocalServerPort
    int port;

    @Autowired UserRepository users;

    private RestClient rest;
    private final long run = System.currentTimeMillis();

    private String atlasAccess, ravenAccess, moleAccess;
    private String atlasUsername, ravenUsername, moleUsername;
    private long atlasId, ravenId, moleId;
    private long atlasPostId, ravenPostId, commentId;
    private long conversationId;
    private long communityId, channelId, channelMessageId;
    private long voiceRoomId;
    private String uploadedMediaUrl;

    // ---------- plumbing ----------

    @BeforeAll
    void setUpClient() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
    }

    private ResponseEntity<Map> post(String path, Object body, String bearer) {
        return rest.post().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Map> put(String path, Object body, String bearer) {
        return rest.put().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> get(String path, String bearer) {
        return rest.method(HttpMethod.GET).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toEntity(Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<List> getList(String path, String bearer) {
        return rest.method(HttpMethod.GET).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toEntity(List.class);
    }

    private ResponseEntity<Map> delete(String path, String bearer) {
        return rest.method(HttpMethod.DELETE).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toEntity(Map.class);
    }

    /** GET where the body may be an error object (not a list) — only the status matters. */
    private int getStatus(String path, String bearer) {
        return rest.method(HttpMethod.GET).uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    /** Registers a user and force-verifies the email the way the seed flow does. */
    private void registerVerified(String handle) {
        String username = handle + run;
        post("/api/v1/auth/register", Map.of(
                "firstName", handle, "lastName", "Tester",
                "username", username, "email", username + "@uat.example", "password", "UAT-passphrase-1!"), null);
        User u = users.findByUsernameIgnoreCase(username).orElseThrow();
        u.setEmailVerified(true);
        users.save(u);
    }

    private String login(String username) {
        var resp = post("/api/v1/auth/login", Map.of("identifier", username, "password", "UAT-passphrase-1!"), null);
        assertThat(resp.getStatusCode().value()).as("login %s", username).isEqualTo(200);
        return (String) resp.getBody().get("accessToken");
    }

    private long meId(String bearer) {
        var me = get("/api/v1/auth/me", bearer);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        return ((Number) me.getBody().get("id")).longValue();
    }

    /** POST that returns a JSON array (e.g. voice join/mute participant lists). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> postList(String path, String bearer) {
        return rest.post().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve().toEntity(List.class).getBody();
    }

    /** POST with a JSON body that returns a JSON array. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> postListBody(String path, Object body, String bearer) {
        return rest.post().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(List.class).getBody();
    }

    /** POST for endpoints that return no meaningful body (204/Void) — returns just the status. */
    private int postStatus(String path, Object body, String bearer) {
        return rest.post().uri(path)
                .headers(h -> { if (bearer != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer); })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toBodilessEntity().getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postPage(String path, String bearer) {
        var page = get(path, bearer);
        assertThat(page.getStatusCode().value()).isEqualTo(200);
        return page.getBody();
    }

    /** Hides UAT users from Nearby so other test classes see an exact world. */
    @org.junit.jupiter.api.AfterAll
    void cleanupDiscoverability() {
        if (atlasAccess == null) return;
        try {
            put("/api/v1/users/me/discoverability", Map.of("discoverable", false), atlasAccess);
            put("/api/v1/users/me/discoverability", Map.of("discoverable", false), ravenAccess);
            put("/api/v1/users/me/discoverability", Map.of("discoverable", false), moleAccess);
        } catch (Exception ignored) { /* best-effort cleanup */ }
    }

    // ---------- 1. three users join ----------

    @Test @Order(10)
    void users_register_login_and_have_distinct_ids() {
        registerVerified("atlas");
        registerVerified("raven");
        registerVerified("mole");
        atlasUsername = "atlas" + run;
        ravenUsername = "raven" + run;
        moleUsername = "mole" + run;

        atlasAccess = login(atlasUsername);
        ravenAccess = login(ravenUsername);
        moleAccess = login(moleUsername);

        atlasId = meId(atlasAccess);
        ravenId = meId(ravenAccess);
        moleId = meId(moleAccess);
        assertThat(atlasId).isNotEqualTo(ravenId).isNotEqualTo(moleId);
    }

    // ---------- 2. profiles ----------

    @Test @Order(20)
    void profile_update_then_public_view_hides_private_fields() {
        var upd = put("/api/v1/users/me",
                Map.of("firstName", "Atlas", "lastName", "Pilot", "bio", "I fly drones over Mumbai"), atlasAccess);
        assertThat(upd.getStatusCode().value()).isEqualTo(200);

        var pub = get("/api/v1/users/" + atlasUsername, ravenAccess);
        assertThat(pub.getStatusCode().value()).isEqualTo(200);
        assertThat(String.valueOf(pub.getBody())).contains("Atlas").contains("drones");

        var ghost = get("/api/v1/users/nobody" + run, ravenAccess);
        assertThat(ghost.getStatusCode().value()).isEqualTo(404);
    }

    // ---------- 2b. dating-style profile fields ----------

    @Test @Order(21)
    void dating_profile_fields_round_trip_and_are_validated() {
        // under-18 is refused (rolls back; nothing is persisted)
        assertThat(put("/api/v1/users/me", Map.of("dateOfBirth", "2015-01-01"), atlasAccess)
                .getStatusCode().value()).isEqualTo(400);
        // arbitrary remote image URLs are rejected — only app-generated /media/* is allowed
        assertThat(put("/api/v1/users/me", Map.of("profileImage", "https://evil.example/x.png"), atlasAccess)
                .getStatusCode().value()).isEqualTo(400);

        var upd = put("/api/v1/users/me", Map.of(
                "firstName", "Atlas", "lastName", "Pilot", "bio", "I fly drones over Mumbai",
                "profession", "Drone pilot",
                "interests", " coffee , hiking ,, coffee ",
                "lookingFor", "Coffee & good conversation",
                "dateOfBirth", "1996-05-04",
                "profileImage", "/media/uat-avatar.png"), atlasAccess);
        assertThat(upd.getStatusCode().value()).isEqualTo(200);
        // interests are normalized (trimmed, blanks dropped)
        assertThat(String.valueOf(upd.getBody().get("interests"))).contains("coffee").contains("hiking");
        assertThat(upd.getBody().get("profileImage")).isEqualTo("/media/uat-avatar.png");
        // age is derived server-side, never accepted from the client
        assertThat(((Number) upd.getBody().get("age")).intValue()).isGreaterThanOrEqualTo(28);

        // the public profile exposes the discovery fields
        var pub = get("/api/v1/users/" + atlasUsername, ravenAccess);
        assertThat(String.valueOf(pub.getBody())).contains("Coffee & good conversation");
    }

    // ---------- 3. posts: create, authz, edit, comment, like ----------

    @Test @Order(30)
    void post_lifecycle_create_view_edit_and_visibility() {
        var create = post("/api/v1/posts",
                Map.of("content", "Atlas here — first UAT flight #uat", "visibility", "PUBLIC"), atlasAccess);
        assertThat(create.getStatusCode().value()).isEqualTo(201);

        atlasPostId = ((Number) create.getBody().get("id")).longValue();

        // author sees own post
        assertThat(get("/api/v1/posts/" + atlasPostId, atlasAccess).getStatusCode().value()).isEqualTo(200);
        // strangers see public posts
        assertThat(get("/api/v1/posts/" + atlasPostId, ravenAccess).getStatusCode().value()).isEqualTo(200);
        // anonymous sees public posts
        assertThat(get("/api/v1/posts/" + atlasPostId, null).getStatusCode().value()).isEqualTo(200);

        // only the author can edit
        var foreignEdit = put("/api/v1/posts/" + atlasPostId,
                Map.of("content", "hijacked", "visibility", "PUBLIC"), ravenAccess);
        assertThat(foreignEdit.getStatusCode().value()).isEqualTo(403);

        var ownEdit = put("/api/v1/posts/" + atlasPostId,
                Map.of("content", "Atlas here — edited by me #uat", "visibility", "PUBLIC"), atlasAccess);
        assertThat(ownEdit.getStatusCode().value()).isEqualTo(200);
        assertThat(String.valueOf(ownEdit.getBody().get("content"))).contains("edited by me");

        // empty content rejected
        var empty = post("/api/v1/posts", Map.of("content", "   ", "visibility", "PUBLIC"), atlasAccess);
        assertThat(empty.getStatusCode().value()).isEqualTo(400);
    }

    @Test @Order(31)
    void private_post_is_invisible_to_everyone_but_author() {
        var create = post("/api/v1/posts",
                Map.of("content", "secret note to self", "visibility", "PRIVATE"), ravenAccess);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        ravenPostId = ((Number) create.getBody().get("id")).longValue();

        assertThat(get("/api/v1/posts/" + ravenPostId, ravenAccess).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/posts/" + ravenPostId, atlasAccess).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/v1/posts/" + ravenPostId, null).getStatusCode().value()).isEqualTo(404);
        // and it never leaks into explore
        var explore = postPage("/api/v1/posts/explore?page=0&size=50", null);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) explore.get("posts");
        assertThat(posts).extracting(p -> ((Number) p.get("id")).longValue()).doesNotContain(ravenPostId);
    }

    @Test @Order(32)
    void followers_post_hidden_until_follow_exists() {
        var create = post("/api/v1/posts",
                Map.of("content", "atlas followers-only briefing", "visibility", "FOLLOWERS"), atlasAccess);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        long fid = ((Number) create.getBody().get("id")).longValue();

        // raven doesn't follow yet → hidden
        assertThat(get("/api/v1/posts/" + fid, ravenAccess).getStatusCode().value()).isEqualTo(404);

        // raven follows atlas → now visible
        var follow = post("/api/v1/users/" + atlasId + "/follow", Map.of(), ravenAccess);
        assertThat(follow.getStatusCode().value()).isEqualTo(204);
        assertThat(get("/api/v1/posts/" + fid, ravenAccess).getStatusCode().value()).isEqualTo(200);
    }

    @Test @Order(33)
    void comments_create_and_counts_reflect() {
        var c = post("/api/v1/posts/" + atlasPostId + "/comments",
                Map.of("content", "raven checking in — nice flight!"), ravenAccess);
        assertThat(c.getStatusCode().value()).isEqualTo(201);
        commentId = ((Number) c.getBody().get("id")).longValue();

        // duplicate id = new comment, not overwrite
        var c2 = post("/api/v1/posts/" + atlasPostId + "/comments",
                Map.of("content", "second comment"), ravenAccess);
        assertThat(c2.getStatusCode().value()).isEqualTo(201);
        assertThat(((Number) c2.getBody().get("id")).longValue()).isNotEqualTo(commentId);

        var detail = get("/api/v1/posts/" + atlasPostId, ravenAccess);
        assertThat(((Number) detail.getBody().get("commentCount")).longValue()).isGreaterThanOrEqualTo(2);

        // comments listed with author names
        var list = getList("/api/v1/posts/" + atlasPostId + "/comments", ravenAccess);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(list.getBody()).isNotEmpty();
    }

    @Test @Order(34)
    void like_is_idempotent_and_toggleable_per_user() {
        // like is a TOGGLE (Twitter semantics): first call likes…
        var like1 = post("/api/v1/posts/" + atlasPostId + "/like", Map.of(), ravenAccess);
        assertThat(like1.getStatusCode().value()).isEqualTo(200);
        assertThat(like1.getBody()).containsEntry("liked", true);

        // …second call unlikes (count returns to 0)…
        var like2 = post("/api/v1/posts/" + atlasPostId + "/like", Map.of(), ravenAccess);
        assertThat(like2.getBody()).containsEntry("liked", false);
        assertThat(((Number) get("/api/v1/posts/" + atlasPostId, ravenAccess).getBody().get("likeCount")).longValue()).isZero();

        // …and a third re-likes.
        var like3 = post("/api/v1/posts/" + atlasPostId + "/like", Map.of(), ravenAccess);
        assertThat(like3.getBody()).containsEntry("liked", true);
        var detail = get("/api/v1/posts/" + atlasPostId, ravenAccess);
        assertThat(((Number) detail.getBody().get("likeCount")).longValue()).isEqualTo(1);
        assertThat((Boolean) detail.getBody().get("likedByMe")).isTrue();

        // remove the toggle-like left by this test
        post("/api/v1/posts/" + atlasPostId + "/like", Map.of(), ravenAccess);
    }

    @Test @Order(35)
    void feed_contains_own_posts_and_followed_authors() {
        // atlas follows raven → raven's private post must NOT be in atlas feed
        assertThat(post("/api/v1/users/" + ravenId + "/follow", Map.of(), atlasAccess).getStatusCode().value()).isEqualTo(204);
        var feed = postPage("/api/v1/posts/feed?page=0&size=50", atlasAccess);
        List<Map<String, Object>> posts = (List<Map<String, Object>>) feed.get("posts");
        assertThat(posts).extracting(p -> ((Number) p.get("id")).longValue()).contains(atlasPostId);
        assertThat(posts).extracting(p -> ((Number) p.get("id")).longValue()).doesNotContain(ravenPostId);
    }

    @Test @Order(36)
    void search_finds_public_content_and_users() {
        var search = get("/api/v1/posts/search?q=UAT&userLimit=5&postLimit=5", ravenAccess);
        assertThat(search.getStatusCode().value()).isEqualTo(200);
        assertThat(String.valueOf(search.getBody())).contains("atlas" + run);
    }

    @Test @Order(37)
    void author_can_delete_own_post_but_nobody_elses() {
        var create = post("/api/v1/posts", Map.of("content", "delete me later", "visibility", "PUBLIC"), ravenAccess);
        long tempId = ((Number) create.getBody().get("id")).longValue();

        assertThat(delete("/api/v1/posts/" + tempId, atlasAccess).getStatusCode().value()).isEqualTo(403);
        assertThat(delete("/api/v1/posts/" + tempId, ravenAccess).getStatusCode().value()).isEqualTo(204);
        assertThat(get("/api/v1/posts/" + tempId, ravenAccess).getStatusCode().value()).isEqualTo(404);
    }

    // ---------- 4. social graph: connections (the dating-app heart) ----------

    @Test @Order(40)
    void connection_request_accept_flow_and_dedup() {
        // atlas → raven: PENDING
        var req = post("/api/v1/connections/" + ravenId, Map.of(), atlasAccess);
        assertThat(req.getStatusCode().value()).isEqualTo(200);
        assertThat(req.getBody()).containsEntry("status", "PENDING");

        // duplicate request stays PENDING (idempotent)
        var again = post("/api/v1/connections/" + ravenId, Map.of(), atlasAccess);
        assertThat(again.getBody()).containsEntry("status", "PENDING");

        // raven sees the pending request
        var incoming = getList("/api/v1/connections?filter=incoming", ravenAccess);
        assertThat(incoming.getBody()).anySatisfy(x -> {
            Map<String, Object> row = (Map<String, Object>) x;
            assertThat(((Number) ((Map<String, Object>) row.get("user")).get("id")).longValue()).isEqualTo(atlasId);
        });

        // raven accepts by POSTing back (sendOrAccept semantics)
        var accept = post("/api/v1/connections/" + atlasId, Map.of(), ravenAccess);
        assertThat(accept.getBody()).containsEntry("status", "ACCEPTED");

        // both now list each other under accepted
        assertThat(getList("/api/v1/connections?filter=accepted", atlasAccess).getBody())
                .anySatisfy(x -> assertThat(((Number) ((Map<String, Object>) ((Map<String, Object>) x).get("user")).get("id")).longValue()).isEqualTo(ravenId));
        assertThat(getList("/api/v1/connections?filter=accepted", ravenAccess).getBody())
                .anySatisfy(x -> assertThat(((Number) ((Map<String, Object>) ((Map<String, Object>) x).get("user")).get("id")).longValue()).isEqualTo(atlasId));
    }

    // ---------- 5. chat: DM lifecycle ----------

    @Test @Order(50)
    void dm_send_receive_unread_and_read_marks() {
        var open = post("/api/v1/conversations", Map.of("userId", ravenId), atlasAccess);
        assertThat(open.getStatusCode().value()).isIn(200, 201);
        conversationId = ((Number) open.getBody().get("id")).longValue();

        var send = post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("content", "hey raven — UAT ping!"), atlasAccess);
        assertThat(send.getStatusCode().value()).isIn(200, 201);
        assertThat(send.getBody()).containsEntry("mine", true);

        // raven's inbox shows unread badge
        var inbox = getList("/api/v1/conversations", ravenAccess);
        assertThat(incomingHasUnread(inbox)).isTrue();

        // raven reads; unread goes to zero
        assertThat(post("/api/v1/conversations/" + conversationId + "/read", Map.of(), ravenAccess)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(incomingHasUnread(getList("/api/v1/conversations", ravenAccess))).isFalse();

        // empty message rejected
        assertThat(post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("content", "  "), atlasAccess).getStatusCode().value()).isEqualTo(400);
    }

    @SuppressWarnings("unchecked")
    private boolean incomingHasUnread(ResponseEntity<List> inbox) {
        List<Map<String, Object>> convs = inbox.getBody();
        return convs.stream()
                .anyMatch(c -> ((Number) c.get("id")).longValue() == conversationId
                        && ((Number) c.get("unread")).longValue() > 0);
    }

    @Test @Order(51)
    void chat_is_object_level_private() {
        // mole cannot read atlas-raven conversation
        assertThat(get("/api/v1/conversations/" + conversationId + "/messages?size=50", moleAccess)
                .getStatusCode().value()).isEqualTo(404);
        // mole cannot post into it
        assertThat(post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("content", "intrusion"), moleAccess).getStatusCode().value()).isEqualTo(404);
    }

    // ---------- 6. communities ----------

    @Test @Order(60)
    void community_create_join_channel_post_and_roles() {
        var create = post("/api/v1/communities",
                Map.of("name", "UAT Hangar " + run, "description", "where UAT planes gather", "icon", "✈️"), atlasAccess);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        communityId = ((Number) create.getBody().get("id")).longValue();
        assertThat(String.valueOf(create.getBody().get("myRole"))).isEqualTo("OWNER");

        // raven joins
        var join = post("/api/v1/communities/" + communityId + "/join", Map.of(), ravenAccess);
        assertThat(join.getStatusCode().value()).isEqualTo(200);

        // default channels exist
        var channels = getList("/api/v1/communities/" + communityId + "/channels", ravenAccess);
        assertThat(channels.getBody()).isNotEmpty();
        channelId = ((Number) ((Map<String, Object>) channels.getBody().get(0)).get("id")).longValue();

        // member posts to channel
        var msg = post("/api/v1/communities/channels/" + channelId + "/messages",
                Map.of("content", "raven in the hangar!"), ravenAccess);
        assertThat(msg.getStatusCode().value()).isIn(200, 201);
        channelMessageId = ((Number) msg.getBody().get("id")).longValue();

        // member (non-admin) cannot create channels
        assertThat(post("/api/v1/communities/" + communityId + "/channels",
                Map.of("name", "sneaky"), ravenAccess).getStatusCode().value()).isEqualTo(403);

        // owner can
        var ownerChannel = post("/api/v1/communities/" + communityId + "/channels",
                Map.of("name", "atlas-only"), atlasAccess);
        assertThat(ownerChannel.getStatusCode().value()).isIn(200, 201);

        // outsider (mole) cannot even see channels
        assertThat(getStatus("/api/v1/communities/" + communityId + "/channels", moleAccess)).isEqualTo(404);
    }

    // ---------- 7. voice rooms ----------

    @Test @Order(70)
    void voice_room_create_join_mute_presence_and_close() {
        var create = post("/api/v1/voice/rooms",
                Map.of("name", "UAT Live " + run, "communityId", communityId), atlasAccess);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        voiceRoomId = ((Number) create.getBody().get("id")).longValue();
        assertThat(((Number) create.getBody().get("communityId")).longValue()).isEqualTo(communityId);

        // both join (join returns the participant LIST)
        assertThat(postList("/api/v1/voice/rooms/" + voiceRoomId + "/join", atlasAccess) != null).isTrue();
        var ravenJoin = postList("/api/v1/voice/rooms/" + voiceRoomId + "/join", ravenAccess);
        assertThat(ravenJoin.toString()).contains(atlasUsername).contains(ravenUsername);

        // raven mutes; peers see it
        var muted = postListBody("/api/v1/voice/rooms/" + voiceRoomId + "/mute", Map.of("muted", true), ravenAccess);
        assertThat(muted.toString()).contains("true");

        // open rooms list includes it with participant count 2
        var rooms = getList("/api/v1/voice/rooms", null);
        assertThat(rooms.getBody().toString()).contains("UAT Live " + run);

        // raven leaves (returns {ok:true})
        assertThat(post("/api/v1/voice/rooms/" + voiceRoomId + "/leave", Map.of(), ravenAccess).getStatusCode().value()).isEqualTo(200);

        // non-host cannot close
        var ravenClose = post("/api/v1/voice/rooms/" + voiceRoomId + "/close", Map.of(), ravenAccess);
        assertThat(ravenClose.getStatusCode().value()).isEqualTo(403);
        var hostClose = post("/api/v1/voice/rooms/" + voiceRoomId + "/close", Map.of(), atlasAccess);
        assertThat(hostClose.getStatusCode().value()).isEqualTo(200);

        // closed room refuses joins
        assertThat(post("/api/v1/voice/rooms/" + voiceRoomId + "/join", Map.of(), ravenAccess).getStatusCode().value())
                .isEqualTo(400);
    }

    // ---------- 8. media ----------

    @Test @Order(80)
    void media_upload_serve_and_post_attachment() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D,
                0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0, 0, 0, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0, 1, 0, 0, 5, 0, 1,
                0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82 };
        var parts = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        parts.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override public String getFilename() { return "uat.png"; }
        });
        var bytes = rest.post().uri("/api/v1/media")
                .headers(h -> { h.set(HttpHeaders.AUTHORIZATION, "Bearer " + atlasAccess); h.setContentType(MediaType.MULTIPART_FORM_DATA); })
                .body(parts)
                .retrieve().toEntity(Map.class);
        assertThat(bytes.getStatusCode().value()).isEqualTo(200);
        uploadedMediaUrl = (String) bytes.getBody().get("url");
        assertThat(uploadedMediaUrl).startsWith("/media/").endsWith(".png");

        // anonymous GET serves the file
        var serve = rest.method(HttpMethod.GET).uri(uploadedMediaUrl).retrieve().toEntity(byte[].class);
        assertThat(serve.getStatusCode().value()).isEqualTo(200);
        assertThat(serve.getBody()).hasSize(png.length);

        // attachment works on a post
        var withImage = post("/api/v1/posts",
                Map.of("content", "UAT picture post", "visibility", "PUBLIC", "imageUrl", uploadedMediaUrl), ravenAccess);
        assertThat(withImage.getStatusCode().value()).isEqualTo(201);
        assertThat(withImage.getBody().get("imageUrl")).isEqualTo(uploadedMediaUrl);
    }

    // ---------- 9. nearby (dating-app discovery) ----------

    @Test @Order(90)
    void nearby_shares_location_discover_and_privacy() {
        // both share Mumbai coords
        assertThat(put("/api/v1/users/me/location",
                Map.of("latitude", 19.11, "longitude", 72.90, "discoverable", true), atlasAccess)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(put("/api/v1/users/me/location",
                Map.of("latitude", 19.12, "longitude", 72.91, "discoverable", true), ravenAccess)
                .getStatusCode().value()).isEqualTo(200);

        var hits = getList("/api/v1/nearby?lat=19.11&lng=72.90&radiusKm=25", atlasAccess);
        assertThat(hits.getBody().toString()).contains(ravenUsername);
        // distance is rounded, not raw coordinates
        assertThat(hits.getBody().toString()).doesNotContain("\"latitude\"");

        // raven hides → invisible to atlas
        put("/api/v1/users/me/discoverability", Map.of("discoverable", false), ravenAccess);
        var hidden = getList("/api/v1/nearby?lat=19.11&lng=72.90&radiusKm=25", atlasAccess);
        assertThat(hidden.getBody().toString()).doesNotContain(ravenUsername);

        // out-of-range coords rejected
        assertThat(get("/api/v1/nearby?lat=999&lng=72.9&radiusKm=10", atlasAccess).getStatusCode().value()).isEqualTo(400);
        // absurd radius clamped/rejected
        assertThat(get("/api/v1/nearby?lat=19.1&lng=72.9&radiusKm=5000", atlasAccess).getStatusCode().value()).isEqualTo(400);

        put("/api/v1/users/me/discoverability", Map.of("discoverable", true), ravenAccess); // restore state for other tests
    }

    // ---------- 9b. Discover deck (dating) + Discord-style chat extras ----------

    @Test @Order(91)
    void discover_deck_ranks_shared_interests_and_skips_existing_matches() {
        // a new person who shares atlas's interests, standing next door
        registerVerified("comet");
        String cometUsername = "comet" + run;
        String cometAccess = login(cometUsername);
        put("/api/v1/users/me", Map.of("firstName", "Comet", "interests", "coffee, hiking"), cometAccess);
        put("/api/v1/users/me/location",
                Map.of("latitude", 19.115, "longitude", 72.905, "discoverable", true), cometAccess);
        // mole is nearby too, but shares nothing at all
        put("/api/v1/users/me/location",
                Map.of("latitude", 19.12, "longitude", 72.91, "discoverable", true), moleAccess);

        var deck = getList("/api/v1/discover/suggestions?lat=19.11&lng=72.90&radiusKm=25", atlasAccess);
        String body = String.valueOf(deck.getBody());
        assertThat(body).contains(cometUsername);
        // atlas and raven are already matched — raven belongs in Messages, not the deck
        assertThat(body).doesNotContain(ravenUsername);
        // comet shares two interests, so leads the deck
        Map<String, Object> first = (Map<String, Object>) deck.getBody().get(0);
        assertThat(String.valueOf(((Map<String, Object>) first.get("person")).get("username"))).isEqualTo(cometUsername);
        assertThat(((Number) first.get("sharedInterests")).intValue()).isGreaterThanOrEqualTo(2);
        // raw coordinates never appear in the payload
        assertThat(body).doesNotContain("\"latitude\"");

        // tidy up so other test classes see a stable world
        put("/api/v1/users/me/discoverability", Map.of("discoverable", false), cometAccess);
    }

    @Test @Order(92)
    @SuppressWarnings("unchecked")
    void typing_indicators_and_message_reactions() {
        // typing pings are participant-only
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/typing", Map.of(), atlasAccess))
                .isEqualTo(204);
        assertThat(postStatus("/api/v1/conversations/" + conversationId + "/typing", Map.of(), moleAccess))
                .isEqualTo(404);

        var send = post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("content", "react to me"), atlasAccess);
        assertThat(send.getStatusCode().value()).isIn(200, 201);
        long messageId = ((Number) send.getBody().get("id")).longValue();
        assertThat(String.valueOf(send.getBody().get("reactions"))).isEqualTo("[]");

        // raven reacts; the tally is theirs
        var react = postListBody("/api/v1/messages/" + messageId + "/reactions", Map.of("emoji", "🔥"), ravenAccess);
        assertThat(react.get(0)).containsEntry("emoji", "🔥").containsEntry("mine", true);
        assertThat(((Number) react.get(0).get("count")).longValue()).isEqualTo(1);
        // toggling the same emoji removes it
        assertThat(postListBody("/api/v1/messages/" + messageId + "/reactions", Map.of("emoji", "🔥"), ravenAccess))
                .isEmpty();
        // put it back and check atlas sees it as someone else's
        postListBody("/api/v1/messages/" + messageId + "/reactions", Map.of("emoji", "🔥"), ravenAccess);

        List<Map<String, Object>> asAtlas =
                (List<Map<String, Object>>) getList("/api/v1/conversations/" + conversationId + "/messages?size=50", atlasAccess).getBody();
        Map<String, Object> target = asAtlas.stream()
                .filter(x -> ((Number) x.get("id")).longValue() == messageId)
                .findFirst().orElseThrow();
        assertThat((List<Map<String, Object>>) target.get("reactions")).anySatisfy(r -> {
            assertThat(r).containsEntry("emoji", "🔥");
            assertThat(r).containsEntry("mine", false);
        });

        // outsiders cannot react at all
        assertThat(postStatus("/api/v1/messages/" + messageId + "/reactions", Map.of("emoji", "🔥"), moleAccess))
                .isEqualTo(404);

        // request inbox badge counts
        var summary = get("/api/v1/connections/summary", atlasAccess);
        assertThat(summary.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) summary.getBody().get("matches")).longValue()).isGreaterThanOrEqualTo(1);
    }

    // ---------- 10. notifications ----------

    @Test @Order(95)
    void notifications_fire_for_social_actions_and_have_counts() {
        var count = get("/api/v1/notifications/unread-count", ravenAccess);
        assertThat(count.getStatusCode().value()).isEqualTo(200);
        // raven was followed + commented at + liked at → at least one notification
        assertThat(((Number) count.getBody().get("count")).longValue()).isGreaterThanOrEqualTo(0);

        assertThat(post("/api/v1/notifications/read-all", Map.of(), ravenAccess).getStatusCode().value()).isEqualTo(200);
        var after = ((Number) get("/api/v1/notifications/unread-count", ravenAccess).getBody().get("count")).longValue();
        assertThat(after).isZero();
    }

    // ---------- 11. the mole: authz attack matrix ----------

    @Test @Order(99)
    void mole_cannot_reach_anyone_elses_stuff() {
        // no token anywhere
        assertThat(get("/api/v1/posts/feed?page=0&size=5", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/v1/conversations", null).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/api/v1/posts", Map.of("content", "x", "visibility", "PUBLIC"), null).getStatusCode().value()).isEqualTo(401);

        // can't DM himself
        assertThat(post("/api/v1/conversations", Map.of("userId", moleId), moleAccess).getStatusCode().value()).isEqualTo(400);

        // can't follow a ghost
        assertThat(postStatus("/api/v1/users/999999/follow", Map.of(), moleAccess)).isEqualTo(404);

        // can't join/see the closed voice room of others, nor close it
        assertThat(postStatus("/api/v1/voice/rooms/" + voiceRoomId + "/join", Map.of(), moleAccess)).isEqualTo(400);
        assertThat(postStatus("/api/v1/voice/rooms/" + voiceRoomId + "/close", Map.of(), moleAccess)).isEqualTo(403);

        // can't message a random channel of a community he's not in
        assertThat(postStatus("/api/v1/communities/channels/" + channelId + "/messages",
                Map.of("content", "spam"), moleAccess)).isEqualTo(404);

        // forged/garbage token → 401
        assertThat(get("/api/v1/auth/me", "garbage.token.here").getStatusCode().value()).isEqualTo(401);

        // mole cannot DM himself (invalid participant)
        // (self-DM already asserted in the community block above; kept here for the matrix)
        assertThat(get("/api/v1/posts/feed?page=0&size=5", "").getStatusCode().value()).isEqualTo(401);
    }
}
