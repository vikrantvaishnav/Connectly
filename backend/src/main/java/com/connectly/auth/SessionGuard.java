package com.connectly.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Session revocations that must persist even when the caller aborts with an
 * exception (e.g. token-reuse detection throws 401 after revoking everything).
 */
@Service
public class SessionGuard {

    private final AuthSessionRepository sessions;

    public SessionGuard(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {
        sessions.revokeAllForUser(userId, Instant.now());
    }
}
