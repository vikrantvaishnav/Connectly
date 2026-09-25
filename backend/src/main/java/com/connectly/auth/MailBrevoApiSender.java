package com.connectly.auth;

import com.connectly.common.error.ApiException;
import com.connectly.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Delivers email through Brevo's HTTPS API (POST /smtp/email). Chosen over
 * SMTP when cloud hosts block outbound SMTP ports or when Brevo's SMTP IP
 * allowlist is enabled — the API runs over 443, which is never blocked.
 */
@Component
class MailBrevoApiSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(MailBrevoApiSender.class);

    private final JwtProperties props;
    private final RestClient rest;

    MailBrevoApiSender(JwtProperties props) {
        this.props = props;
        this.rest = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", props.mail().brevoApiKey() == null ? "" : props.mail().brevoApiKey())
                .defaultHeader("accept", "application/json")
                .build();
    }

    @Override
    public boolean isConfigured() {
        return "brevo-api".equalsIgnoreCase(props.mail().mode())
                && props.mail().brevoApiKey() != null
                && !props.mail().brevoApiKey().isBlank();
    }

    @Override
    public String mode() {
        return "brevo-api";
    }

    @Override
    public void send(String to, String subject, String body) {
        // MAIL_FROM may be "Name <addr>" or a bare address; Brevo wants them split.
        String from = props.mail().from() == null ? "" : props.mail().from().trim();
        String fromEmail = from.contains("<") ? from.substring(from.indexOf('<') + 1, from.indexOf('>')).trim() : from;
        String fromName = from.contains("<") ? from.substring(0, from.indexOf('<')).trim() : "Connectly";

        try {
            rest.post().uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of("email", fromEmail, "name", fromName),
                            "to", List.of(Map.of("email", to)),
                            "subject", subject,
                            "textContent", body))
                    .retrieve()
                    .toBodilessEntity();
            log.info("MAIL sent via brevo-api to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("MAIL brevo-api send FAILED to={} subject={} : {}", to, subject, e.toString());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_SEND_FAILED",
                    "Could not send the activation email right now. Please try again in a moment.");
        }
    }
}
