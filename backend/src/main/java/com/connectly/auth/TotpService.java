package com.connectly.auth;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** RFC 6238 TOTP for authenticator apps (Google Authenticator, Aegis, 1Password…). */
@Service
public class TotpService {

    private static final Duration STEP = Duration.ofSeconds(30);
    private final TimeBasedOneTimePasswordGenerator totp;
    private final Base32 base32 = new Base32();

    public TotpService() throws NoSuchAlgorithmException {
        this.totp = new TimeBasedOneTimePasswordGenerator(STEP, 6);
    }

    /** New Base32 secret for a user; kept on the user row until confirmed. */
    public String generateSecret() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA1);
            keyGen.init(160);
            return base32.encodeToString(keyGen.generateKey().getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String otpauthUrl(String secret, String username) {
        return "otpauth://totp/Connectly:" + username
                + "?secret=" + secret
                + "&issuer=Connectly"
                + "&algorithm=SHA1"
                + "&digits=6"
                + "&period=30";
    }

    /** Verify a 6-digit code, allowing ±1 step of clock drift. */
    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] keyBytes = base32.decode(secret);
        javax.crypto.spec.SecretKeySpec key =
                new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA1");

        int now = (int) (Instant.now().toEpochMilli() / STEP.toMillis());
        for (int drift = -1; drift <= 1; drift++) {
            try {
                int expected = totp.generateOneTimePassword(key, Instant.ofEpochMilli((now + (long) drift) * STEP.toMillis()));
                if (Integer.parseInt(code) == expected) {
                    return true;
                }
            } catch (InvalidKeyException e) {
                return false;
            }
        }
        return false;
    }
}
