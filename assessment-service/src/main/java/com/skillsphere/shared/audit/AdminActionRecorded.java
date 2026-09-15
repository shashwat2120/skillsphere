package com.skillsphere.shared.audit;

import java.time.Instant;

/**
 * Published every time an admin action happens in this process, in place of
 * the direct {@code admin_audit_log} write this module made before the
 * database-per-service split.
 *
 * <p>Under Sprint 5's single shared database, {@code admin_audit_log} lived
 * in the open shared kernel specifically so both identity (suspending a
 * user) and skill (editing the skill graph) could write to it without skill
 * taking a dependency on identity — see {@code AdminAuditLog}'s own class
 * comment in identity-service, which still carries that reasoning. That
 * design was correct for one database. Once skill's process (this one) and
 * identity's process own separate databases, "write the shared table
 * directly" stops being available to either side but one, and identity —
 * already the natural home for "who did what" across the whole system — is
 * the one that keeps the table. This event is what replaces the write on
 * this side: identity-service's AdminAuditEventListener consumes it and
 * persists the row identically to how AuditLogger used to save it directly.
 */
public record AdminActionRecorded(
        Long adminId,
        String action,
        String targetType,
        Long targetId,
        String beforeState,
        String afterState,
        String ipAddress,
        String reason,
        Instant occurredAt) {
}
