package com.connectly.auth;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Opaque tokens: 256-bit secure random, stored only as SHA-256 hashes. */
@Service
public class TokenService {

    private final SecureRandom random = new SecureRandom();

    public String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateRecoveryCode() {
        // e.g. 4XK9-2M7Q — 8 chars from an unambiguous alphabet
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
