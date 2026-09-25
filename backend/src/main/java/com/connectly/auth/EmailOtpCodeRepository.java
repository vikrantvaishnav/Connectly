package com.connectly.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EmailOtpCodeRepository extends JpaRepository<EmailOtpCode, Long> {

    Optional<EmailOtpCode> findByEmailIgnoreCase(String email);

    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, Instant after);

    long deleteByEmailIgnoreCase(String email);
}
