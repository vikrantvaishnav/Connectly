package com.connectly.auth;

import com.connectly.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only helper (src/test, never packaged in the app): flow tests opt in
 * with {@code connectly.tests.auto-activate=true} so their register→login
 * helpers keep working without performing the OTP step, which
 * AuthFlowIntegrationTest covers end-to-end instead. Disabled by default —
 * registration without the property behaves exactly like production.
 */
@Component
public class TestRegistrationActivator {

    private final boolean enabled;
    private final UserRepository users;
    private final EmailOtpCodeRepository otps;

    public TestRegistrationActivator(UserRepository users, EmailOtpCodeRepository otps,
            @Value("${connectly.tests.auto-activate:false}") boolean enabled) {
        this.users = users;
        this.otps = otps;
        this.enabled = enabled;
    }

    @EventListener
    @Transactional
    public void onRegistration(RegistrationCompletedEvent event) {
        if (!enabled) return;
        users.findById(event.userId()).ifPresent(u -> {
            u.setEmailVerified(true);
            users.save(u);
            otps.deleteByEmailIgnoreCase(u.getEmail());
        });
    }
}
