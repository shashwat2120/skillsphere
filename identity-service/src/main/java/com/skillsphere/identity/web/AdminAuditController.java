package com.skillsphere.identity.web;

import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import com.skillsphere.shared.audit.AdminAuditLog;
import com.skillsphere.shared.audit.AdminAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The audit log viewer.
 *
 * <p>Reads {@code admin_audit_log}, which entries from several modules write
 * to through {@link com.skillsphere.shared.audit.AuditLogger} — this
 * controller only owns the read side. It lives in identity because identity's
 * own package-info already names "the audit trail" as part of what this
 * module is responsible for, and because resolving an admin id to an email
 * needs {@link UserRepository}, which identity may use freely while other
 * modules (skill, in particular) may not.
 *
 * <p>Entries whose target lives outside identity — a skill, say — are shown
 * with their raw {@code targetType}/{@code targetId} rather than a resolved
 * name, since this module cannot reach into skill's tables to look one up.
 * The before/after JSON carries the human-readable detail (the skill's name
 * at the time of the change) for exactly that reason.
 */
@RestController
@RequestMapping("/api/admin/audit-log")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Audit log (admin)", description = "Every administrative action, with before/after state")
public class AdminAuditController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final AdminAuditLogRepository logs;
    private final UserRepository users;

    public record AuditEntryView(
            Long id, Long adminId, String adminEmail, String action,
            String targetType, Long targetId, String beforeState, String afterState,
            String ipAddress, String reason, Instant createdAt) {
    }

    @GetMapping
    @Operation(summary = "Recent administrative actions",
            description = "Newest first, optionally narrowed to one target type (e.g. USER, SKILL). "
                    + "Every entry carries the acting admin, the before/after state, and — for actions "
                    + "that required one — the reason given.")
    public List<AuditEntryView> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {

        int cappedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<AdminAuditLog> entries = targetType == null
                ? logs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, cappedLimit))
                : logs.findByTargetTypeOrderByCreatedAtDesc(targetType.toUpperCase(), PageRequest.of(0, cappedLimit));

        Map<Long, String> adminEmails = users.findAllById(
                        entries.stream().map(AdminAuditLog::getAdminId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        return entries.stream()
                .map(e -> new AuditEntryView(
                        e.getId(), e.getAdminId(), adminEmails.getOrDefault(e.getAdminId(), "unknown"),
                        e.getAction(), e.getTargetType(), e.getTargetId(),
                        e.getBeforeState(), e.getAfterState(), e.getIpAddress(), e.getReason(), e.getCreatedAt()))
                .toList();
    }
}
