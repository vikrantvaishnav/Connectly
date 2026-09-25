package com.connectly.auth;

/**
 * Strategy for delivering transactional email. Implementations: SMTP
 * (MailSmtpSender) and Brevo's HTTP API (MailBrevoApiSender, runs over 443 —
 * immune to SMTP port blocks and Brevo's SMTP IP allowlist).
 */
interface MailSender {

    /** True when this sender is fully configured and should handle delivery. */
    boolean isConfigured();

    void send(String to, String subject, String body);

    /** The mode name this sender handles (matches app.mail.mode values). */
    String mode();
}
