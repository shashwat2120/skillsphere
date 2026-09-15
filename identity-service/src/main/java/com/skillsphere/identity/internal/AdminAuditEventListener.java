package com.skillsphere.identity.internal;

import com.skillsphere.shared.audit.AdminAuditLog;
import com.skillsphere.shared.audit.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Persists an admin action another process recorded, into the one
 * {@code admin_audit_log} table this service now owns.
 *
 * <p>Kafka, not a direct write from the other side — assessment-service's
 * skill-catalog admin actions (SKILL_CREATED, SKILL_PREREQUISITE_ADDED, and
 * so on) used to write this table directly, back when both processes shared
 * one database. See {@code AdminAuditLog}'s own class comment for why that
 * table lived in the shared kernel in the first place, and
 * {@code AdminActionRecordedEvent}'s comment for the wire shape this
 * consumes. This listener is this table's second writer, the same role
 * {@link com.skillsphere.shared.audit.AuditLogger} plays for this service's
 * own admin actions — the two never race, each owns a disjoint set of
 * {@code action} values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditEventListener {

    private final AdminAuditLogRepository logs;

    @KafkaListener(topics = "admin-audit-events", groupId = "identity-service")
    public void on(AdminActionRecordedEvent event) {
        logs.save(new AdminAuditLog(
                event.adminId(), event.action(), event.targetType(), event.targetId(),
                event.beforeState(), event.afterState(), event.ipAddress(), event.reason()));

        log.debug("Recorded admin audit entry {} on {} {} from Kafka",
                event.action(), event.targetType(), event.targetId());
    }
}
