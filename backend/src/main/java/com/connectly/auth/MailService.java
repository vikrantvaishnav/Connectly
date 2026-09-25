package com.connectly.auth;

import com.connectly.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends transactional email. Dispatches to the configured {@link MailSender}:
 * "smtp" (classic SMTP with hard timeouts), "brevo-api" (Brevo HTTPS API —
 * immune to SMTP port blocks and IP allowlists), or "log" (default for
 * dev/test — every mail is printed as a structured log line).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JwtProperties props;
    private final List<MailSender> senders;

    public MailService(JwtProperties props, List<MailSender> senders) {
        this.props = props;
        this.senders = senders;
    }

    public void sendRegistrationOtp(String to, String code) {
        String subject = "Your Connectly activation code: " + code;
        String body = """
                Welcome to Connectly!

                Your activation code is: %s

                It expires in 15 minutes. Enter it in the app to activate your account.
                If you didn't create an account, you can ignore this email.
                """.formatted(code);
        deliver(to, subject, body);
    }

    public void sendVerificationEmail(String to, String token) {
        String subject = "Verify your Connectly email";
        String body = """
                Welcome to Connectly!

                Verify your email address:
                %s/verify-email/%s

                This link expires in 24 hours. If you didn't create an account, ignore this email.
                """.formatted(props.mail().publicBaseUrl(), token);
        deliver(to, subject, body);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String subject = "Reset your Connectly password";
        String body = """
                A password reset was requested for your account.

                Reset link (valid 1 hour):
                %s/reset-password/%s

                If this wasn't you, ignore this email — your password stays unchanged.
                """.formatted(props.mail().publicBaseUrl(), token);
        deliver(to, subject, body);
    }

    public void sendSecurityAlert(String to, String subject, String detail) {
        deliver(to, subject, detail);
    }

    private void deliver(String to, String subject, String body) {
        String mode = props.mail().mode() == null ? "log" : props.mail().mode().toLowerCase();

        MailSender chosen = senders.stream()
                .filter(MailSender::isConfigured)
                .filter(s -> s.mode().equalsIgnoreCase(mode))
                .findFirst()
                .orElse(null);

        if (chosen != null) {
            chosen.send(to, subject, body);
            return;
        }

        // Unknown/misconfigured mode: fail fast so the caller's transaction
        // rolls back and the user sees a retryable error, not a silent drop.
        if ("smtp".equals(mode) || "brevo-api".equals(mode)) {
            log.error("MAIL mode={} but its sender is not usable (missing MAIL_HOST/credentials or BREVO_API_KEY) to={} subject={}",
                    mode, to, subject);
            throw new com.connectly.common.error.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_SEND_FAILED",
                    "Could not send the activation email right now. Please try again in a moment.");
        }

        log.info("MAIL[log-mode] to={} subject={} body:\n{}", to, subject, body);
    }
}
