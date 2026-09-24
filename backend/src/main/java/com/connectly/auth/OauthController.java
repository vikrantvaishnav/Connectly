package com.connectly.auth;

import com.connectly.security.JwtProperties;
import com.connectly.security.JwtService;
import com.connectly.security.WebUtil;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OauthController {

    public record OauthStatus(boolean googleEnabled) {}

    private final GoogleOauthService google;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final AuthSessionRepository sessions;
    private final JwtService jwtService;
    private final com.connectly.auth.TokenService tokenService;
    private final JwtProperties props;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ConcurrentHashMap<String, Boolean> pendingStates = new ConcurrentHashMap<>();

    public OauthController(GoogleOauthService google, UserRepository users,
            UserProfileRepository profiles, AuthSessionRepository sessions,
            JwtService jwtService, com.connectly.auth.TokenService tokenService,
            JwtProperties props, PasswordEncoder passwordEncoder, AuditService audit) {
        this.google = google;
        this.users = users;
        this.profiles = profiles;
        this.sessions = sessions;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @GetMapping("/status")
    public OauthStatus status() {
        return new OauthStatus(google.isEnabled());
    }

    @GetMapping("/google")
    public void start(HttpServletResponse response) throws IOException {
        if (!google.isEnabled()) {
            response.sendError(404);
            return;
        }
        String state = tokenService.generateToken();
        pendingStates.put(state, Boolean.TRUE); // one-time CSRF token
        response.sendRedirect(google.buildAuthUrl(state));
    }

    @GetMapping("/google/callback")
    public void callback(@RequestParam String code, @RequestParam String state,
            HttpServletRequest http, HttpServletResponse response) throws IOException {
        if (pendingStates.remove(state) == null) {
            response.sendError(400, "Invalid OAuth state");
            return;
        }

        GoogleOauthService.GoogleProfile profile = google.exchangeAndVerify(code);
        User user = provisionOrLogin(profile, http);

        String access = jwtService.createAccessToken(user);
        String refresh = createSession(user, http);

        String fragment = "access=" + Base64.getUrlEncoder().encodeToString(access.getBytes())
                + "&refresh=" + Base64.getUrlEncoder().encodeToString(refresh.getBytes());
        String frontendOrigin = props.cors().allowedOrigins().get(0);
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", frontendOrigin + "/oauth/callback#" + fragment);
    }

    private String createSession(User user, HttpServletRequest http) {
        String refresh = jwtService.createRefreshToken(user);
        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setRefreshTokenHash(tokenService.sha256(refresh));
        session.setDevice("Google · " + WebUtil.deviceLabel(http));
        session.setIp(WebUtil.clientIp(http));
        session.setUserAgent(WebUtil.userAgent(http));
        session.setExpiresAt(Instant.now().plusSeconds(props.jwt().refreshTtlSeconds()));
        sessions.save(session);
        return refresh;
    }

    private User provisionOrLogin(GoogleOauthService.GoogleProfile profile, HttpServletRequest http) {
        String email = profile.email().toLowerCase(Locale.ROOT);
        Optional<User> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = existing.get();
            audit.record(user.getId(), AuditService.LOGIN_SUCCESS, "google oauth",
                    WebUtil.clientIp(http), WebUtil.userAgent(http));
            return user;
        }

        User user = new User();
        user.setUsername(availableUsername(email));
        user.setEmail(email);
        // random unusable password; Google logins never use it
        user.setPasswordHash(passwordEncoder.encode(tokenService.generateToken() + tokenService.generateToken()));
        user.setEmailVerified(true);
        users.save(user);

        UserProfile p = new UserProfile();
        p.setUserId(user.getId());
        if (profile.name() != null && !profile.name().isBlank()) {
            String[] parts = profile.name().trim().split("\\s+", 2);
            p.setFirstName(parts[0]);
            if (parts.length > 1) p.setLastName(parts[1]);
        }
        profiles.save(p);

        audit.record(user.getId(), AuditService.REGISTER, "via google", WebUtil.clientIp(http), null);
        return user;
    }

    private String availableUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-z0-9_.]", "");
        if (base.length() < 3) base = "user";
        String candidate = base.length() > 20 ? base.substring(0, 20) : base;
        SecureRandom random = new SecureRandom();
        while (users.existsByUsernameIgnoreCase(candidate)) {
            candidate = (base.length() > 16 ? base.substring(0, 16) : base)
                    + "_" + random.nextInt(10_000);
        }
        return candidate;
    }
}
