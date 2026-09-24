package com.connectly.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import com.connectly.user.User;

@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_SESSION = "sid";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String TYPE_MFA = "mfa";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        return createAccessToken(user, null);
    }

    /**
     * Access token, optionally carrying the session id (`sid`). The sessions
     * endpoint compares it against its own row to mark "this device".
     */
    public String createAccessToken(User user, Long sessionId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim("username", user.getUsername());
        if (sessionId != null) {
            builder.claim(CLAIM_SESSION, sessionId);
        }
        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.jwt().accessTtlSeconds())))
                .signWith(key)
                .compact();
    }

    /** Refresh token; the session row is keyed by SHA-256 of this token. Unique jti guarantees uniqueness even within the same second. */
    public String createRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.jwt().refreshTtlSeconds())))
                .signWith(key)
                .compact();
    }

    /** Short-lived token proving "password ok, awaiting 2nd factor". */
    public String createMfaToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_MFA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.jwt().mfaTtlSeconds())))
                .signWith(key)
                .compact();
    }

    /** Returns claims if signature and type are valid and token not expired. */
    public Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return props.jwt().accessTtlSeconds();
    }

    public long mfaTtlSeconds() {
        return props.jwt().mfaTtlSeconds();
    }
}
