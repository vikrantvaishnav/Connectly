package com.connectly.auth;

import com.connectly.security.JwtProperties;
import com.connectly.user.User;
import com.connectly.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists login attempt counters in an independent transaction so that a
 * failed login (which throws 401 and rolls back the outer transaction) still
 * records the failure. Without this, lockout would never accumulate.
 */
@Service
public class LoginGuard {

    private final UserRepository users;
    private final JwtProperties props;

    public LoginGuard(UserRepository users, JwtProperties props) {
        this.users = users;
        this.props = props;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(User user) {
        user.registerFailedLogin(props.login().maxFailedAttempts(), props.login().lockMinutes());
        users.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(User user) {
        user.registerSuccessfulLogin();
        users.save(user);
    }
}
