package com.connectly.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Detaches audit rows from a deleted account (audit_logs has no FK by design). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update AuditLog a set a.userId = null where a.userId = :userId")
    void detachUser(@org.springframework.data.repository.query.Param("userId") Long userId);
}
