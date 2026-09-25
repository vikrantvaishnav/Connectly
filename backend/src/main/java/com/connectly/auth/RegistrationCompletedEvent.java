package com.connectly.auth;

/**
 * Published after a registration transaction commits. Test contexts use it to
 * auto-verify fresh accounts; production code ignores it.
 */
public record RegistrationCompletedEvent(Long userId, String email) {
}
