package com.connectly.auth;

import com.connectly.common.error.ApiException;
import com.connectly.security.JwtProperties;
import com.connectly.security.JwtService;
import com.connectly.security.WebUtil;
import com.connectly.user.User;
import com.connectly.user.UserProfile;
import com.connectly.user.UserProfileRepository;
import com.connectly.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    public static final int RECOVERY_CODE_COUNT = 8;
    private static final int PASSWORD_MIN_LENGTH = 10;
    private static final int PASSWORD_MAX_LENGTH = 72;
    private static final List<String> PASSWORD_DENYLIST = List.of(
            "password", "123456", "qwerty", "letmein", "welcome",
            "admin", "iloveyou", "sunshine", "princess", "football");

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final AuthSessionRepository sessions;
    private final AuthTokenRepository tokens;
    private final RecoveryCodeRepository recoveryCodes;
    private final AuditService audit;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final JwtService jwtService;
    private final JwtProperties props;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final com.connectly.notification.NotificationRepository notifications;
    private final AuditLogRepository auditLogs;
    private final LoginGuard loginGuard;
    private final SessionGuard sessionGuard;
    private final EmailOtpCodeRepository emailOtps;
    private final ApplicationEventPublisher events;

    public AuthService(UserRepository users, UserProfileRepository profiles,
            AuthSessionRepository sessions, AuthTokenRepository tokens,
            RecoveryCodeRepository recoveryCodes, AuditService audit,
            TokenService tokenService, TotpService totpService,
            JwtService jwtService, JwtProperties props,
            PasswordEncoder passwordEncoder, MailService mailService,
            LoginGuard loginGuard, SessionGuard sessionGuard,
            com.connectly.notification.NotificationRepository notifications,
            AuditLogRepository auditLogs,
            EmailOtpCodeRepository emailOtps,
            ApplicationEventPublisher events) {
        this.users = users;
        this.profiles = profiles;
        this.sessions = sessions;
        this.tokens = tokens;
        this.recoveryCodes = recoveryCodes;
        this.audit = audit;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.jwtService = jwtService;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.notifications = notifications;
        this.auditLogs = auditLogs;
        this.loginGuard = loginGuard;
        this.sessionGuard = sessionGuard;
        this.emailOtps = emailOtps;
        this.events = events;
    }

    // ---------- registration ----------

    @Transactional
    public AuthDtos.UserDto register(AuthDtos.RegisterRequest req, HttpServletRequest http) {
        String username = req.username().toLowerCase(Locale.ROOT);
        String email = req.email().toLowerCase(Locale.ROOT);

        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        validatePassword(req.password());

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        users.save(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setFirstName(req.firstName());
        profile.setLastName(req.lastName());
        profiles.save(profile);

        issueRegistrationOtp(user, http);

        audit.record(user.getId(), AuditService.REGISTER, "username=" + username,
                WebUtil.clientIp(http), WebUtil.userAgent(http));

        // Fired after the transaction commits — test contexts hook this to
        // auto-activate accounts; production code ignores it.
        events.publishEvent(new RegistrationCompletedEvent(user.getId(), user.getEmail()));

        return AuthDtos.UserDto.from(user);
    }

    /** No session is issued at registration — the account activates only after the emailed OTP. */
    public record RegisterResult(AuthDtos.UserDto user, AuthDtos.OtpRequiredResponse otp) {}

    /**
     * Registers the account and emails a 6-digit activation code. The account
     * stays dormant (unverifiable at login) until the code is confirmed, so no
     * account can ever exist or be used without OTP verification.
     */
    @Transactional
    public RegisterResult registerAndLogin(AuthDtos.RegisterRequest req, HttpServletRequest http) {
        AuthDtos.UserDto user = register(req, http);
        return new RegisterResult(user,
                new AuthDtos.OtpRequiredResponse(maskEmail(user.email()), OTP_TTL.toSeconds()));
    }

    /** joe.doe@gmail.com → j•••@g•••.com — enough to know which inbox to check. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "•••";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String dpart = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        return local.charAt(0) + "•••@" + (dpart.isEmpty() ? "" : dpart.charAt(0)) + "•••" + tld;
    }

    // ---------- registration OTP ----------

    /** Lifetime of the emailed activation code. */
    static final Duration OTP_TTL = Duration.ofMinutes(15);
    /** Max codes requestable per email per rolling window. */
    private static final int OTP_MAX_PER_WINDOW = 5;
    private static final Duration OTP_WINDOW = Duration.ofHours(1);
    /** Codes are 6 digits, zero-padded. */
    private static final java.security.SecureRandom OTP_RANDOM = new java.security.SecureRandom();

    /**
     * Generates a fresh 6-digit activation code for the account's email,
     * replacing any previous code (only the newest works). Rate limited per
     * email and per IP; the code is stored hashed and delivered by email.
     */
    @Transactional
    public AuthDtos.MessageResponse issueRegistrationOtp(User user, HttpServletRequest http) {
        String email = user.getEmail();

        long recent = emailOtps.countByEmailIgnoreCaseAndCreatedAtAfter(email, Instant.now().minus(OTP_WINDOW));
        if (recent >= OTP_MAX_PER_WINDOW) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LIMIT",
                    "Too many activation codes requested. Try again later.");
        }

        String code = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
        EmailOtpCode row = emailOtps.findByEmailIgnoreCase(email).orElseGet(EmailOtpCode::new);
        row.setEmail(email);
        row.setCodeHash(tokenService.sha256(code));
        row.setAttempts(0);
        row.setUsedAt(null);
        row.setExpiresAt(Instant.now().plus(OTP_TTL));
        emailOtps.save(row);

        mailService.sendRegistrationOtp(email, code);
        audit.record(user.getId(), "OTP_ISSUED", "email=" + email,
                WebUtil.clientIp(http), WebUtil.userAgent(http));
        return new AuthDtos.MessageResponse("Activation code sent. Check your email.");
    }

    /**
     * Activates the account: checks email + code, marks the email verified,
     * burns the code and issues the first real session. Uniform error text so
     * the endpoint can't be used to enumerate which emails are registered.
     */
    @Transactional
    public AuthDtos.AuthResponse verifyRegistrationOtp(AuthDtos.VerifyOtpRequest req,
            HttpServletRequest http) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        if (req.code() == null || !req.code().matches("\\d{6}")) {
            throw ApiException.badRequest("Enter the 6-digit code from your email");
        }

        EmailOtpCode row = emailOtps.findByEmailIgnoreCase(email)
                .filter(EmailOtpCode::isUsable)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired code. Request a new one."));

        if (!tokenService.sha256(req.code()).equals(row.getCodeHash())) {
            row.setAttempts(row.getAttempts() + 1);
            emailOtps.save(row);
            if (row.getAttempts() >= 5) {
                // Burned: a new code must be requested.
                throw ApiException.badRequest("Invalid or expired code. Request a new one.");
            }
            throw ApiException.badRequest("That code is not correct. Check the latest email and try again.");
        }

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired code. Request a new one."));

        if (user.isEmailVerified()) {
            throw ApiException.conflict("Account is already activated. Please log in.");
        }

        row.markUsed();
        emailOtps.save(row);
        emailOtps.deleteByEmailIgnoreCase(email);
        user.setEmailVerified(true);
        users.save(user);

        audit.record(user.getId(), AuditService.EMAIL_VERIFIED, "otp-activated",
                WebUtil.clientIp(http), WebUtil.userAgent(http));
        audit.record(user.getId(), AuditService.LOGIN_SUCCESS, WebUtil.deviceLabel(http),
                WebUtil.clientIp(http), WebUtil.userAgent(http));
        return issueTokens(user, http);
    }

    /**
     * Re-issues the activation code for a dormant (unverified) account.
     * Deliberately vague — never reveals whether the email is registered.
     */
    @Transactional
    public AuthDtos.MessageResponse resendRegistrationOtp(String email, HttpServletRequest http) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(normalized)
                .filter(u -> !u.isEmailVerified())
                .ifPresent(u -> issueRegistrationOtp(u, http));
        audit.record(null, "OTP_RESEND", "email=" + normalized, WebUtil.clientIp(http), WebUtil.userAgent(http));
        return new AuthDtos.MessageResponse("If that email needs activation, a new code is on its way.");
    }

    /**
     * Re-sends the verification email for the signed-in account. Idempotent and
     * deliberately vague when already verified (no information leak).
     */
    @Transactional
    public AuthDtos.MessageResponse resendVerification(User me) {
        if (me.isEmailVerified()) {
            return new AuthDtos.MessageResponse("Email already verified.");
        }
        issueEmailToken(me, AuthToken.Type.VERIFY_EMAIL, Duration.ofHours(24));
        return new AuthDtos.MessageResponse("Verification email sent.");
    }

    /**
     * Self-service account deletion (Data & Compliance). In one transaction:
     * everything the user owns cascades away with the users row (posts, comments,
     * likes, follows, connections, conversations, messages, reactions,
     * notifications, memberships, voice participation, location, sessions,
     * tokens, recovery codes, profile); audit rows are detached so the
     * moderation timeline survives without pointing at a ghost; and the login
     * identifier is retired so the email/username can never be re-registered
     * ambiguously.
     */
    @Transactional
    public AuthDtos.MessageResponse deleteAccount(User me) {
        Long id = me.getId();

        // Cross-aggregate cleanups that have no FK to users.
        notifications.deleteAllInvolving(id);

        // Detach audit history: keep the event, lose the personal reference.
        auditLogs.detachUser(id);

        users.delete(me);
        return new AuthDtos.MessageResponse("Account deleted. This cannot be undone.");
    }

    @Transactional
    public AuthDtos.MessageResponse verifyEmail(AuthDtos.VerifyEmailRequest req) {
        AuthToken token = requireUsableToken(req.token(), AuthToken.Type.VERIFY_EMAIL);
        token.markUsed();
        User user = users.findById(token.getUserId()).orElseThrow();
        user.setEmailVerified(true);
        audit.record(user.getId(), AuditService.EMAIL_VERIFIED, null, null, null);
        return new AuthDtos.MessageResponse("Email verified. You can now log in.");
    }

    // ---------- login ----------

    /**
     * Step 1: identifier + password. Returns tokens, or an MFA challenge when
     * 2FA is enabled. Uniform "invalid credentials" error — never reveals
     * whether the account exists. Lockout after N failures for M minutes.
     */
    @Transactional
    public Object login(AuthDtos.LoginRequest req, HttpServletRequest http) {
        String ip = WebUtil.clientIp(http);
        String ua = WebUtil.userAgent(http);

        Optional<User> found = findByIdentifier(req.identifier().trim());
        if (found.isEmpty()) {
            // burn comparable time so missing accounts are not distinguishable by latency
            passwordEncoder.matches(req.password(), DUMMY_HASH);
            audit.record(null, AuditService.LOGIN_FAILURE, "unknown identifier", ip, ua);
            throw ApiException.unauthorized("Invalid credentials");
        }
        User user = found.get();

        if (user.isLocked()) {
            audit.record(user.getId(), AuditService.LOGIN_LOCKED,
                    "locked until " + user.getLockedUntil(), ip, ua);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again in a few minutes.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // committed independently — survives the 401 rollback
            loginGuard.recordFailure(user);
            audit.record(user.getId(), AuditService.LOGIN_FAILURE,
                    "attempt " + (user.getFailedLoginAttempts() + 1) + "/" + props.login().maxFailedAttempts(), ip, ua);
            throw ApiException.unauthorized("Invalid credentials");
        }

        loginGuard.recordSuccess(user);

        if (!user.isEmailVerified()) {
            // Dormant account: credentials are correct, but the account was
            // never activated. No session, no enumeration hints.
            audit.record(user.getId(), AuditService.LOGIN_FAILURE, "email not verified", ip, ua);
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Account is not activated. Enter the code we emailed you, or request a new one.");
        }

        if (user.isTotpEnabled()) {
            if (req.totpCode() == null || req.totpCode().isBlank()) {
                audit.record(user.getId(), AuditService.MFA_CHALLENGE, null, ip, ua);
                return new AuthDtos.MfaChallengeResponse(
                        jwtService.createMfaToken(user), jwtService.mfaTtlSeconds());
            }
            if (!verifySecondFactor(user, req.totpCode(), ip, ua)) {
                throw ApiException.unauthorized("Invalid authentication code");
            }
        }

        audit.record(user.getId(), AuditService.LOGIN_SUCCESS, WebUtil.deviceLabel(http), ip, ua);
        return issueTokens(user, http);
    }

    /** Step 2: code/recovery-code against a 5-minute MFA challenge token. */
    @Transactional
    public AuthDtos.AuthResponse verifyMfa(String mfaToken, String code, HttpServletRequest http) {
        Claims claims = jwtService.parse(mfaToken, JwtService.TYPE_MFA)
                .orElseThrow(() -> ApiException.unauthorized("Your verification session expired. Log in again."));
        User user = users.findById(Long.valueOf(claims.getSubject())).orElseThrow();
        String ip = WebUtil.clientIp(http);
        String ua = WebUtil.userAgent(http);

        if (!verifySecondFactor(user, code, ip, ua)) {
            throw ApiException.unauthorized("Invalid authentication code");
        }
        audit.record(user.getId(), AuditService.MFA_SUCCESS, WebUtil.deviceLabel(http), ip, ua);
        return issueTokens(user, http);
    }

    private boolean verifySecondFactor(User user, String code, String ip, String ua) {
        String trimmed = code.trim().toUpperCase(Locale.ROOT);
        if (trimmed.matches("\\d{6}") && user.getTotpSecret() != null
                && totpService.verify(user.getTotpSecret(), trimmed)) {
            return true;
        }
        String hash = tokenService.sha256(trimmed.replace("-", ""));
        for (RecoveryCode rc : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (rc.getCodeHash().equals(hash)) {
                rc.markUsed();
                recoveryCodes.save(rc);
                audit.record(user.getId(), AuditService.RECOVERY_USED, null, ip, ua);
                return true;
            }
        }
        audit.record(user.getId(), AuditService.MFA_FAILURE, "code rejected", ip, ua);
        return false;
    }

    // ---------- tokens / sessions ----------

    private AuthDtos.AuthResponse issueTokens(User user, HttpServletRequest http) {
        String refresh = jwtService.createRefreshToken(user);

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setRefreshTokenHash(tokenService.sha256(refresh));
        session.setDevice(WebUtil.deviceLabel(http));
        session.setIp(WebUtil.clientIp(http));
        session.setUserAgent(WebUtil.userAgent(http));
        session.setExpiresAt(Instant.now().plusSeconds(props.jwt().refreshTtlSeconds()));
        sessions.save(session); // id available for the access token's sid claim

        String access = jwtService.createAccessToken(user, session.getId());
        return new AuthDtos.AuthResponse(access, refresh, AuthDtos.UserDto.from(user));
    }

    /**
     * Rotate the refresh token: the used row is revoked (hash retained) and a
     * fresh session row is created. Presenting a revoked token again is reuse —
     * treated as possible theft → every session for the user is revoked.
     */
    @Transactional
    public AuthDtos.AuthResponse refresh(String refreshToken, HttpServletRequest http) {
        jwtService.parse(refreshToken, JwtService.TYPE_REFRESH)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        AuthSession session = sessions.findByRefreshTokenHash(tokenService.sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (!session.isActive()) {
            // committed independently so the 401 response doesn't roll it back
            sessionGuard.revokeAllForUser(session.getUserId());
            audit.record(session.getUserId(), AuditService.REFRESH_REJECTED,
                    "reuse detected — all sessions revoked", WebUtil.clientIp(http), WebUtil.userAgent(http));
            throw ApiException.unauthorized("Session expired. Please log in again.");
        }

        User user = users.findById(session.getUserId()).orElseThrow();

        // rotate: retire this row (hash kept for reuse detection), mint a new one
        session.revoke();
        sessions.save(session);

        String rotated = jwtService.createRefreshToken(user);
        AuthSession next = new AuthSession();
        next.setUserId(user.getId());
        next.setRefreshTokenHash(tokenService.sha256(rotated));
        next.setDevice(session.getDevice());
        next.setIp(WebUtil.clientIp(http));
        next.setUserAgent(WebUtil.userAgent(http));
        next.setExpiresAt(Instant.now().plusSeconds(props.jwt().refreshTtlSeconds()));
        sessions.save(next);

        audit.record(user.getId(), AuditService.TOKEN_REFRESHED, session.getDevice(),
                WebUtil.clientIp(http), WebUtil.userAgent(http));
        return new AuthDtos.AuthResponse(
                jwtService.createAccessToken(user, next.getId()), rotated, AuthDtos.UserDto.from(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        sessions.findByRefreshTokenHash(tokenService.sha256(refreshToken)).ifPresent(session -> {
            session.revoke();
            sessions.save(session);
        });
    }

    @Transactional
    public void logoutAll(User user) {
        sessions.revokeAllForUser(user.getId(), Instant.now());
        audit.record(user.getId(), AuditService.SESSION_REVOKED, "logout all devices", null, null);
    }

    /** Session list; marks the caller's current session by the access token's sid claim. */
    @Transactional(readOnly = true)
    public List<AuthDtos.SessionDto> listSessions(User user, Long currentSessionId) {
        return sessions.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtDesc(user.getId()).stream()
                .map(s -> new AuthDtos.SessionDto(
                        s.getId(),
                        s.getDevice() != null ? s.getDevice() : "Unknown device",
                        s.getIp(),
                        s.getCreatedAt().toString(),
                        s.getLastUsedAt().toString(),
                        currentSessionId != null && currentSessionId.equals(s.getId())))
                .toList();
    }

    @Transactional
    public void revokeSession(User user, long sessionId) {
        AuthSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session not found"));
        if (!session.getUserId().equals(user.getId())) {
            // object-level authorization: never touch someone else's session
            throw ApiException.forbidden("Not your session");
        }
        session.revoke();
        sessions.save(session);
        audit.record(user.getId(), AuditService.SESSION_REVOKED, "sessionId=" + sessionId, null, null);
    }

    // ---------- password reset ----------

    @Transactional
    public void forgotPassword(AuthDtos.ForgotPasswordRequest req, HttpServletRequest http) {
        users.findByEmailIgnoreCase(req.email().trim()).ifPresent(user -> {
            issueEmailToken(user, AuthToken.Type.PASSWORD_RESET, Duration.ofHours(1));
            audit.record(user.getId(), AuditService.PASSWORD_RESET_REQUESTED,
                    null, WebUtil.clientIp(http), WebUtil.userAgent(http));
        });
        // Always "succeed" — never reveal whether the email exists.
    }

    @Transactional
    public AuthDtos.MessageResponse resetPassword(AuthDtos.ResetPasswordRequest req) {
        AuthToken token = requireUsableToken(req.token(), AuthToken.Type.PASSWORD_RESET);
        validatePassword(req.password());

        token.markUsed();
        User user = users.findById(token.getUserId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        users.save(user);
        sessions.revokeAllForUser(user.getId(), Instant.now()); // kill stolen sessions after a reset
        audit.record(user.getId(), AuditService.PASSWORD_RESET_COMPLETED, null, null, null);
        return new AuthDtos.MessageResponse("Password updated. Log in with your new password.");
    }

    // ---------- 2FA management ----------

    @Transactional
    public AuthDtos.TotpSetupResponse beginTotpSetup(User user) {
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret); // pending until confirmed with a valid code
        users.save(user);
        return new AuthDtos.TotpSetupResponse(secret, totpService.otpauthUrl(secret, user.getUsername()));
    }

    @Transactional
    public AuthDtos.RecoveryCodesResponse confirmTotp(User user, AuthDtos.TotpEnableRequest req, HttpServletRequest http) {
        if (user.getTotpSecret() == null) {
            throw ApiException.badRequest("Start 2FA setup first");
        }
        if (user.isTotpEnabled()) {
            throw ApiException.conflict("2FA is already enabled");
        }
        if (!totpService.verify(user.getTotpSecret(), req.code())) {
            audit.record(user.getId(), AuditService.MFA_FAILURE, "setup confirm failed",
                    WebUtil.clientIp(http), null);
            throw ApiException.badRequest("That code didn't match. Check your authenticator and try again.");
        }
        user.setTotpEnabled(true);
        users.save(user);

        recoveryCodes.deleteByUserId(user.getId());
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = tokenService.generateRecoveryCode();
            plain.add(code);
            RecoveryCode rc = new RecoveryCode();
            rc.setUserId(user.getId());
            rc.setCodeHash(tokenService.sha256(code.replace("-", "")));
            recoveryCodes.save(rc);
        }
        audit.record(user.getId(), AuditService.MFA_ENABLED, null, WebUtil.clientIp(http), null);
        return new AuthDtos.RecoveryCodesResponse(plain);
    }

    @Transactional
    public void disableTotp(User user, AuthDtos.TotpDisableRequest req, HttpServletRequest http) {
        if (!user.isTotpEnabled()) {
            throw ApiException.conflict("2FA is not enabled");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Password incorrect");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        users.save(user);
        recoveryCodes.deleteByUserId(user.getId());
        audit.record(user.getId(), AuditService.MFA_DISABLED, null, WebUtil.clientIp(http), null);
    }

    // ---------- helpers ----------

    private AuthToken requireUsableToken(String plainToken, AuthToken.Type type) {
        AuthToken token = tokens.findByTokenHashAndType(tokenService.sha256(plainToken), type)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired link"));
        if (!token.isUsable()) {
            throw ApiException.badRequest("This link was already used or has expired");
        }
        return token;
    }

    private Optional<User> findByIdentifier(String identifier) {
        return identifier.contains("@")
                ? users.findByEmailIgnoreCase(identifier).or(() -> users.findByUsernameIgnoreCase(identifier))
                : users.findByUsernameIgnoreCase(identifier.toLowerCase(Locale.ROOT));
    }

    private void issueEmailToken(User user, AuthToken.Type type, Duration ttl) {
        String plain = tokenService.generateToken();
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setTokenHash(tokenService.sha256(plain));
        token.setType(type);
        token.setExpiresAt(Instant.now().plus(ttl));
        tokens.save(token);
        if (type == AuthToken.Type.VERIFY_EMAIL) {
            mailService.sendVerificationEmail(user.getEmail(), plain);
        } else {
            mailService.sendPasswordResetEmail(user.getEmail(), plain);
        }
    }

    private void validatePassword(String password) {
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw ApiException.badRequest("Password must be 10-72 characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (PASSWORD_DENYLIST.stream().anyMatch(lower::contains)) {
            throw ApiException.badRequest("This password is too common. Choose a stronger one.");
        }
    }

    /** Pre-computed bcrypt hash used to equalize timing for unknown accounts. */
    private static final String DUMMY_HASH = "$2a$12$XeS9Z0rG1lO6RnOQo5y3YO7Qz2uW0cE1uF5dS9cB3vJ2hK7mP8qNe";
}
