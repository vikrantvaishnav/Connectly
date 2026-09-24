package com.connectly.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record JwtProperties(
        Cors cors,
        Jwt jwt,
        Login login,
        RateLimit rateLimit,
        Mail mail,
        Oauth oauth) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, long accessTtlSeconds, long refreshTtlSeconds, long mfaTtlSeconds) {}

    public record Login(int maxFailedAttempts, int lockMinutes) {}

    public record RateLimit(String login, String register, String forgotPassword, String resetPassword, String verifyEmail) {}

    public record Mail(String mode, String from, String publicBaseUrl) {}

    public record Oauth(Google google) {
        public record Google(boolean enabled, String clientId, String clientSecret) {}
    }
}
