package com.connectly.auth;

import com.connectly.user.User;
import com.connectly.user.UserRepository;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired TotpService totpService;
    @Autowired TokenService tokenService;
    @Autowired AuthTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired AuthService authService;

    private RestClient rest;

    @BeforeAll
    void setUpClient() {
        // RestClient that never throws on error statuses; assertions read the status directly
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
    }

    private final String unique = String.valueOf(System.currentTimeMillis());

    // ---------- helpers ----------

    private ResponseEntity<Map> post(String path, Object body) {
        return rest.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map.class);
    }

    private ResponseEntity<java.util.List> getList(String path, String bearerToken) {
        return rest.method(HttpMethod.GET).uri(path)
                .headers(h -> { if (bearerToken != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken); })
                .retrieve()
                .toEntity(java.util.List.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> get(String path, String bearerToken) {
        return rest.method(HttpMethod.GET).uri(path)
                .headers(h -> { if (bearerToken != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken); })
                .retrieve()
                .toEntity(Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> delete(String path, String bearerToken) {
        return rest.method(HttpMethod.DELETE).uri(path)
                .headers(h -> { if (bearerToken != null) h.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken); })
                .retrieve()
                .toEntity(Map.class);
    }

    private Map<String, String> registerAndVerify(String handle) {
        String username = "user" + handle;
        String email = username + "@example.com";
        post("/api/v1/auth/register", Map.of(
                "firstName", "Test", "lastName", "User",
                "username", username, "email", email, "password", "correct-horse-battery"));

        User u = users.findByUsernameIgnoreCase(username).orElseThrow();
        u.setEmailVerified(true);
        users.save(u);
        return Map.of("username", username, "email", email, "password", "correct-horse-battery");
    }

    private String loginAndGetRefresh(String identifier, String password) {
        var resp = post("/api/v1/auth/login", Map.of("identifier", identifier, "password", password));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return (String) resp.getBody().get("refreshToken");
    }

    private String freshAccess(String refresh) {
        var r = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        return (String) r.getBody().get("accessToken");
    }

    // ---------- tests ----------

    @Test
    void registerLoginRefreshRotation_andReuseDetection() {
        var creds = registerAndVerify(unique + "a");
        String refresh1 = loginAndGetRefresh(creds.get("username"), creds.get("password"));

        var r1 = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh1));
        assertThat(r1.getStatusCode().value()).isEqualTo(200);
        String refresh2 = (String) r1.getBody().get("refreshToken");
        assertThat(refresh2).isNotEqualTo(refresh1);

        // old token reuse → 401, all sessions revoked
        var r2 = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh1));
        assertThat(r2.getStatusCode().value()).isEqualTo(401);

        // even the rotated token is dead now
        var r3 = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh2));
        assertThat(r3.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void badPassword_locksAfterFiveAttempts() {
        var creds = registerAndVerify(unique + "b");
        for (int i = 0; i < 5; i++) {
            var resp = post("/api/v1/auth/login",
                    Map.of("identifier", creds.get("username"), "password", "wrong-password-1"));
            assertThat(resp.getStatusCode().value()).isEqualTo(401);
        }
        var locked = post("/api/v1/auth/login",
                Map.of("identifier", creds.get("username"), "password", "wrong-password-1"));
        assertThat(locked.getStatusCode().value()).isEqualTo(429);

        // even the correct password is rejected while locked
        var lockedCorrect = post("/api/v1/auth/login",
                Map.of("identifier", creds.get("username"), "password", creds.get("password")));
        assertThat(lockedCorrect.getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void unknownUser_andWrongPassword_areIndistinguishable() {
        var wrong = post("/api/v1/auth/login",
                Map.of("identifier", "ghost" + unique, "password", "whatever-pass-123"));
        var badPw = post("/api/v1/auth/login",
                Map.of("identifier", "nobody" + unique + "@example.com", "password", "whatever-pass-123"));
        assertThat(wrong.getStatusCode().value()).isEqualTo(401);
        assertThat(badPw.getStatusCode().value()).isEqualTo(401);
        assertThat(wrong.getBody().get("message")).isEqualTo(badPw.getBody().get("message"));
    }

    @Test
    void weakPassword_rejected() {
        var resp = post("/api/v1/auth/register", Map.of(
                "firstName", "A", "lastName", "B", "username", "weakpw" + unique,
                "email", "weakpw" + unique + "@example.com", "password", "password123"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void duplicateUsername_rejected() {
        var creds = registerAndVerify(unique + "f");
        var dup = post("/api/v1/auth/register", Map.of(
                "firstName", "A", "lastName", "B",
                "username", creds.get("username"),
                "email", "other" + unique + "@example.com", "password", "correct-horse-battery"));
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void totpChallenge_thenVerifyIssuesTokens() {
        var creds = registerAndVerify(unique + "c");
        User user = users.findByUsernameIgnoreCase(creds.get("username")).orElseThrow();

        var setup = authService.beginTotpSetup(user);
        authService.confirmTotp(user,
                new AuthDtos.TotpEnableRequest(currentTotp(setup.secret())), new MockHttpServletRequest());

        // login now returns an MFA challenge instead of tokens
        var challenge = post("/api/v1/auth/login",
                Map.of("identifier", creds.get("username"), "password", creds.get("password")));
        assertThat(challenge.getStatusCode().value()).isEqualTo(200);
        assertThat(challenge.getBody()).containsKey("mfaToken");

        var verified = post("/api/v1/auth/mfa/verify", Map.of(
                "mfaToken", challenge.getBody().get("mfaToken"),
                "code", currentTotp(setup.secret())));
        assertThat(verified.getStatusCode().value()).isEqualTo(200);
        assertThat((String) verified.getBody().get("accessToken")).isNotBlank();
        assertThat((String) verified.getBody().get("refreshToken")).isNotBlank();
    }

    @Test
    void mfaVerify_withRecoveryCode_works() {
        var creds = registerAndVerify(unique + "g");
        User user = users.findByUsernameIgnoreCase(creds.get("username")).orElseThrow();

        var setup = authService.beginTotpSetup(user);
        var codes = authService.confirmTotp(user,
                new AuthDtos.TotpEnableRequest(currentTotp(setup.secret())), new MockHttpServletRequest());
        String recovery = codes.codes().get(0); // e.g. "4XK9-2M7Q"

        var challenge = post("/api/v1/auth/login",
                Map.of("identifier", creds.get("username"), "password", creds.get("password")));
        var verified = post("/api/v1/auth/mfa/verify", Map.of(
                "mfaToken", challenge.getBody().get("mfaToken"), "code", recovery));
        assertThat(verified.getStatusCode().value()).isEqualTo(200);

        // the same recovery code cannot be used twice
        var challenge2 = post("/api/v1/auth/login",
                Map.of("identifier", creds.get("username"), "password", creds.get("password")));
        var reused = post("/api/v1/auth/mfa/verify", Map.of(
                "mfaToken", challenge2.getBody().get("mfaToken"), "code", recovery));
        assertThat(reused.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void sessions_listed_and_objectLevelAuthOnRevoke() {
        var creds = registerAndVerify(unique + "d");
        String refresh = loginAndGetRefresh(creds.get("username"), creds.get("password"));
        String access = freshAccess(refresh);

        var list = getList("/api/v1/auth/sessions", access);
        assertThat(list.getStatusCode().value()).isEqualTo(200);

        // nonexistent/foreign session → treated as missing, not leaky
        var revoke = delete("/api/v1/auth/sessions/999999", access);
        assertThat(revoke.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void protectedEndpoint_requiresToken_and_meReturnsUser() {
        var noAuth = get("/api/v1/auth/me", null);
        assertThat(noAuth.getStatusCode().value()).isEqualTo(401);

        var creds = registerAndVerify(unique + "h");
        String access = freshAccess(loginAndGetRefresh(creds.get("username"), creds.get("password")));
        var me = get("/api/v1/auth/me", access);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().get("username")).isEqualTo(creds.get("username"));
    }

    @Test
    void resetPassword_invalidatesOldSessions() {
        var creds = registerAndVerify(unique + "e");
        String refresh = loginAndGetRefresh(creds.get("username"), creds.get("password"));

        User user = users.findByUsernameIgnoreCase(creds.get("username")).orElseThrow();
        String resetToken = mintResetToken(user);

        var resp = post("/api/v1/auth/reset-password",
                Map.of("token", resetToken, "password", "brand-new-passphrase-99"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);

        // old refresh token is dead
        var old = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh));
        assertThat(old.getStatusCode().value()).isEqualTo(401);

        // new password works
        var login = post("/api/v1/auth/login",
                Map.of("identifier", creds.get("username"), "password", "brand-new-passphrase-99"));
        assertThat(login.getStatusCode().value()).isEqualTo(200);

        // reset token is single-use
        var again = post("/api/v1/auth/reset-password",
                Map.of("token", resetToken, "password", "another-passphrase-456"));
        assertThat(again.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void forgotPassword_neverRevealsAccountExistence() {
        var real = registerAndVerify(unique + "i");
        var realResp = post("/api/v1/auth/forgot-password", Map.of("email", real.get("email")));
        var fakeResp = post("/api/v1/auth/forgot-password", Map.of("email", "doesnotexist" + unique + "@example.com"));
        assertThat(realResp.getStatusCode().value()).isEqualTo(200);
        assertThat(fakeResp.getStatusCode().value()).isEqualTo(200);
        assertThat(realResp.getBody().get("message")).isEqualTo(fakeResp.getBody().get("message"));
    }

    // ---------- helpers ----------

    private String mintResetToken(User user) {
        String plain = tokenService.generateToken();
        AuthToken t = new AuthToken();
        t.setUserId(user.getId());
        t.setTokenHash(tokenService.sha256(plain));
        t.setType(AuthToken.Type.PASSWORD_RESET);
        t.setExpiresAt(Instant.now().plusSeconds(3600));
        tokens.save(t);
        return plain;
    }

    private String currentTotp(String secret) {
        try {
            var gen = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
            var key = new SecretKeySpec(new Base32().decode(secret), "HmacSHA1");
            return String.format("%06d", gen.generateOneTimePassword(key, Instant.now()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
