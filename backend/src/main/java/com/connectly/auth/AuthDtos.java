package com.connectly.auth;

import com.connectly.common.error.ApiErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request/response DTOs for the auth API. */
public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 1, max = 80) String firstName,
            @Size(max = 80) String lastName,
            @NotBlank @Pattern(regexp = "^[a-z0-9_.]{3,20}$", message = "3-20 chars: lowercase letters, numbers, _ or .") String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 72, message = "Password must be 10-72 characters") String password) {
    }

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password,
            String totpCode) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 72) String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record TotpSetupResponse(String secret, String otpauthUrl) {
    }

    public record TotpEnableRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record TotpDisableRequest(@NotBlank String password) {
    }

    public record RecoveryCodesResponse(List<String> codes) {
    }

    public record SessionDto(
            long id,
            String device,
            String ip,
            String createdAt,
            String lastUsedAt,
            boolean current) {
    }

    // ---- responses ----

    public record UserDto(
            long id,
            String username,
            String email,
            String firstName,
            String lastName,
            String role,
            boolean emailVerified,
            boolean totpEnabled) {

        public static UserDto from(com.connectly.user.User u) {
            // names live on UserProfile; enrich at the API edge when needed
            return new UserDto(u.getId(), u.getUsername(), u.getEmail(),
                    null, null, u.getRole().name(),
                    u.isEmailVerified(), u.isTotpEnabled());
        }
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            UserDto user) {
    }

    /** Returned when credentials are correct but 2FA is required. */
    public record MfaChallengeResponse(String mfaToken, long expiresInSec) {
    }

    public record MessageResponse(String message) {
    }

    public record ApiError(Instant timestamp, int status, String error, String message, String path) {
        public static ApiError of(ApiErrorResponse r) {
            return new ApiError(r.timestamp(), r.status(), r.error(), r.message(), r.path());
        }
    }
}
