package com.connectly.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Records security-relevant events to DB + structured log line. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String REGISTER = "REGISTER";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    public static final String MFA_CHALLENGE = "MFA_CHALLENGE";
    public static final String MFA_SUCCESS = "MFA_SUCCESS";
    public static final String MFA_FAILURE = "MFA_FAILURE";
    public static final String MFA_ENABLED = "MFA_ENABLED";
    public static final String MFA_DISABLED = "MFA_DISABLED";
    public static final String RECOVERY_USED = "RECOVERY_CODE_USED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String REFRESH_REJECTED = "REFRESH_REJECTED";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(Long userId, String event, String detail, String ip, String userAgent) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setEvent(event);
        entry.setDetail(truncate(detail, 300));
        entry.setIp(truncate(ip, 45));
        entry.setUserAgent(truncate(userAgent, 300));
        repo.save(entry);
        log.info("AUDIT event={} userId={} ip={} detail={}", event, userId, ip, detail);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
