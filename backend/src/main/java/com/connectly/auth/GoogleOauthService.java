package com.connectly.auth;

import com.connectly.common.error.ApiException;
import com.connectly.security.JwtProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Google Sign-In (free tier of Google Cloud console; no billing required).
 * Until GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET are configured, the controller
 * reports { enabled: false } and the frontend hides the Google button.
 */
@Service
public class GoogleOauthService {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    public static final String REDIRECT_URI = "http://localhost:8080/api/v1/auth/oauth/google/callback";

    private final JwtProperties props;
    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleOauthService(JwtProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        var g = props.oauth().google();
        return g.enabled() && g.clientId() != null && !g.clientId().isBlank();
    }

    /** The Google consent-screen URL the user is redirected to. */
    public String buildAuthUrl(String state) {
        var g = props.oauth().google();
        return AUTH_ENDPOINT
                + "?client_id=" + URLEncoder.encode(g.clientId(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&prompt=select_account";
    }

    public record GoogleProfile(String email, String name, String picture, boolean emailVerified) {}

    /** Exchange the authorization code for tokens and verify the ID token with Google. */
    public GoogleProfile exchangeAndVerify(String code) {
        if (!isEnabled()) {
            throw ApiException.badRequest("Google sign-in is not configured on this server");
        }
        var g = props.oauth().google();

        JsonNode tokens = postForm(TOKEN_ENDPOINT, java.util.Map.of(
                "code", code,
                "client_id", g.clientId(),
                "client_secret", g.clientSecret(),
                "redirect_uri", REDIRECT_URI,
                "grant_type", "authorization_code"));
        String idToken = tokens.path("id_token").asText(null);
        if (idToken == null) {
            throw ApiException.badRequest("Google did not return an ID token");
        }
        return verifyIdToken(idToken).orElseThrow(() ->
                ApiException.unauthorized("Google ID token failed verification"));
    }

    /** Verify against Google's public tokeninfo endpoint (validates sig + aud + expiry). */
    private Optional<GoogleProfile> verifyIdToken(String idToken) {
        try {
            JsonNode info = http.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (info == null || !props.oauth().google().clientId().equals(info.path("aud").asText())) {
                return Optional.empty();
            }
            return Optional.of(new GoogleProfile(
                    info.path("email").asText(),
                    info.path("name").asText(null),
                    info.path("picture").asText(null),
                    info.path("email_verified").asBoolean(false)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private JsonNode postForm(String url, java.util.Map<String, String> form) {
        var body = new StringBuilder();
        form.forEach((k, v) -> {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        try {
            return http.post()
                    .uri(url)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body.toString())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw ApiException.badRequest("Could not complete Google sign-in: " + e.getMessage());
        }
    }
}
