package com.skillsphere.shared.audit;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One administrative action, with enough state to answer "who did this, to
 * what, and why" without trusting anyone's memory of it.
 *
 * <p>Append-only by convention, matching every other audit-shaped table in
 * this schema — see {@link com.skillsphere.shared.domain.BaseEntity}'s note
 * that a mutable audit record is not an audit record. Nothing in this module
 * ever updates or deletes a row here.
 *
 * <p>Lives in the shared kernel rather than in identity, even though most
 * entries concern user accounts. Admin actions happen in several modules —
 * suspending a user (identity) and editing the skill graph (skill) are both
 * audited here — and skill is expressly forbidden from depending on identity
 * (see skill's package-info). Putting the table anywhere but the open kernel
 * would force either a dependency skill must not take, or a duplicate audit
 * table per module. {@link #targetType} and {@link #targetId} are a plain
 * string and id rather than a typed reference for the same reason every other
 * cross-module pointer in this codebase is: a foreign key here would be a
 * coupling that cannot survive the Sprint 6 service split.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor
public class AdminAuditLog extends BaseEntity {

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /** Short verb-shaped code, e.g. {@code "USER_SUSPENDED"}, {@code "SKILL_PREREQUISITE_ADDED"}. */
    @Column(nullable = false, length = 60)
    private String action;

    /** What kind of thing changed, e.g. {@code "USER"}, {@code "SKILL"}. */
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private String afterState;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AdminAuditLog(Long adminId, String action, String targetType, Long targetId,
                          String beforeState, String afterState, String ipAddress, String reason) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.ipAddress = ipAddress;
        this.reason = reason;
    }
}
