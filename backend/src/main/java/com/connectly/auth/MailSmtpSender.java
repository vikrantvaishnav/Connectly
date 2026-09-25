package com.connectly.auth;

import com.connectly.common.error.ApiException;
import com.connectly.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Classic SMTP delivery with hard timeouts and fail-fast error mapping. */
@Component
class MailSmtpSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(MailSmtpSender.class);

    private final JwtProperties props;
    private final JavaMailSender mailSender; // null-safe: only used in smtp mode

    MailSmtpSender(JwtProperties props, org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSender) {
        this.props = props;
        this.mailSender = mailSender.getIfAvailable();
    }

    @Override
    public boolean isConfigured() {
        return "smtp".equalsIgnoreCase(props.mail().mode()) && mailSender != null;
    }

    @Override
    public String mode() {
        return "smtp";
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.mail().from());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            // Fail fast and loudly: the caller's transaction rolls back (no
            // half-created accounts), and the user gets a clear retryable error
            // instead of a hanging request.
            log.error("MAIL send FAILED to={} subject={} : {}", to, subject, e.toString());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_SEND_FAILED",
                    "Could not send the activation email right now. Please try again in a moment.");
        }
        log.info("MAIL sent to={} subject={}", to, subject);
    }
}
