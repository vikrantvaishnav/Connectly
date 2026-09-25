package com.connectly.auth;

import com.connectly.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email. In "log" mode (the default for dev/test) every mail
 * is printed as a structured log line instead of being sent — verification and
 * reset links are then visible in the backend console.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JwtProperties props;
    private final JavaMailSender mailSender; // null-safe: only used in smtp mode

    public MailService(JwtProperties props, org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSender) {
        this.props = props;
        this.mailSender = mailSender.getIfAvailable();
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

    /** Sends the 6-digit account activation code. */
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
        if ("smtp".equalsIgnoreCase(props.mail().mode()) && mailSender != null) {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mail().from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("MAIL sent to={} subject={}", to, subject);
        } else {
            log.info("MAIL[log-mode] to={} subject={} body:\n{}", to, subject, body);
        }
    }
}
