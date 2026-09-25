package com.connectly.auth;

import com.connectly.common.error.ApiException;
import com.connectly.security.JwtProperties;
import com.connectly.security.RateLimiter;
import com.connectly.security.WebUtil;
import com.connectly.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final JwtProperties props;

    public AuthController(AuthService authService, RateLimiter rateLimiter, JwtProperties props) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    // ---------- public ----------

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req,
            HttpServletRequest http) {
        limit(http, "register", props.rateLimit().register());
        // Returns a full session: registration logs you in, so a lost/undelivered
        // verification email can never lock someone out of the account they made.
        return ResponseEntity.status(201).body(authService.registerAndLogin(req, http).auth());
    }

    /** Re-send the verification email for the signed-in account. */
    @PostMapping("/resend-verification")
    public AuthDtos.MessageResponse resendVerification(@AuthenticationPrincipal User me,
                                                       HttpServletRequest http) {
        limit(http, "resend-verification", "3/300");
        return authService.resendVerification(me);
    }

    /** Self-service account deletion — permanent, cascades everything, ends the session. */
    @DeleteMapping("/me")
    public AuthDtos.MessageResponse deleteAccount(@AuthenticationPrincipal User me,
                                                  HttpServletRequest http) {
        limit(http, "delete-account", "10/3600");
        var result = authService.deleteAccount(me);
        // The access token dies with the account's sessions; the client clears its own state.
        return result;
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
        limit(http, "login", props.rateLimit().login());
        return authService.login(req, http); // AuthResponse OR MfaChallengeResponse
    }

    @PostMapping("/mfa/verify")
    public AuthDtos.AuthResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest req, HttpServletRequest http) {
        limit(http, "login", props.rateLimit().login());
        return authService.verifyMfa(req.mfaToken(), req.code(), http);
    }

    public record MfaVerifyRequest(String mfaToken, String code) {}

    @PostMapping("/verify-email")
    public AuthDtos.MessageResponse verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest req) {
        return authService.verifyEmail(req);
    }

    @PostMapping("/forgot-password")
    public AuthDtos.MessageResponse forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest req,
            HttpServletRequest http) {
        limit(http, "forgot-password", props.rateLimit().forgotPassword());
        authService.forgotPassword(req, http);
        return new AuthDtos.MessageResponse(
                "If that email exists, a reset link is on its way.");
    }

    @PostMapping("/reset-password")
    public AuthDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req,
            HttpServletRequest http) {
        limit(http, "reset-password", props.rateLimit().resetPassword());
        return authService.resetPassword(req);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest req,
            HttpServletRequest http) {
        return authService.refresh(req.refreshToken(), http);
    }

    @PostMapping("/logout")
    public AuthDtos.MessageResponse logout(@Valid @RequestBody AuthDtos.LogoutRequest req) {
        authService.logout(req.refreshToken());
        return new AuthDtos.MessageResponse("Logged out");
    }

    // ---------- authenticated ----------

    @GetMapping("/me")
    public AuthDtos.UserDto me(@AuthenticationPrincipal User user) {
        return AuthDtos.UserDto.from(user);
    }

    @GetMapping("/sessions")
    public List<AuthDtos.SessionDto> sessions(@AuthenticationPrincipal User user,
            @org.springframework.web.bind.annotation.RequestAttribute(name = "sessionId", required = false) Long sessionId) {
        return authService.listSessions(user, sessionId);
    }

    @DeleteMapping("/sessions/{id}")
    public AuthDtos.MessageResponse revokeSession(@AuthenticationPrincipal User user, @PathVariable long id) {
        authService.revokeSession(user, id);
        return new AuthDtos.MessageResponse("Session revoked");
    }

    @PostMapping("/logout-all")
    public AuthDtos.MessageResponse logoutAll(@AuthenticationPrincipal User user) {
        authService.logoutAll(user);
        return new AuthDtos.MessageResponse("Logged out on all devices");
    }

    // ---------- 2FA ----------

    @PostMapping("/2fa/setup")
    public AuthDtos.TotpSetupResponse setup2fa(@AuthenticationPrincipal User user) {
        return authService.beginTotpSetup(user);
    }

    @PostMapping("/2fa/enable")
    public AuthDtos.RecoveryCodesResponse enable2fa(@AuthenticationPrincipal User user,
            @Valid @RequestBody AuthDtos.TotpEnableRequest req, HttpServletRequest http) {
        return authService.confirmTotp(user, req, http);
    }

    @PostMapping("/2fa/disable")
    public AuthDtos.MessageResponse disable2fa(@AuthenticationPrincipal User user,
            @Valid @RequestBody AuthDtos.TotpDisableRequest req, HttpServletRequest http) {
        authService.disableTotp(user, req, http);
        return new AuthDtos.MessageResponse("2FA disabled");
    }

    private void limit(HttpServletRequest http, String action, String spec) {
        int[] parsed = RateLimiter.parseSpec(spec);
        String key = action + ":" + WebUtil.clientIp(http);
        if (!rateLimiter.allow(key, parsed[0], parsed[1])) {
            throw ApiException.tooManyRequests("Too many requests. Slow down and try again shortly.");
        }
    }
}
