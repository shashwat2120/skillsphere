package com.skillsphere.identity.internal;

import com.skillsphere.identity.domain.AccountStatus;
import com.skillsphere.identity.domain.RefreshToken;
import com.skillsphere.identity.domain.RoleName;
import com.skillsphere.identity.domain.User;
import com.skillsphere.identity.domain.UserRepository;
import com.skillsphere.identity.events.InstructorApproved;
import com.skillsphere.identity.events.AccountSuspended;
import com.skillsphere.identity.web.AdminUserDtos;
import com.skillsphere.shared.audit.AuditLogger;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Administrative control over accounts.
 *
 * <p>Completes the instructor lifecycle. Instructors register as
 * {@code PENDING} and cannot sign in, which is the right default — they author
 * the items that measure other people, so the role is granted by a human. But a
 * gate with no key is just a wall: without this service a pending instructor
 * stays stranded forever, which is what the system did until now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository users;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher events;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<AdminUserDtos.PendingInstructor> listPendingInstructors() {
        return users.findAll().stream()
                .filter(user -> user.getStatus() == AccountStatus.PENDING)
                .filter(user -> user.hasRole(RoleName.INSTRUCTOR))
                .filter(user -> user.getDeletedAt() == null)
                .map(user -> new AdminUserDtos.PendingInstructor(
                        user.getId(), user.getEmail(), user.getFullName(),
                        user.isEmailVerified(), user.getCreatedAt()))
                .toList();
    }

    /**
     * Approves a pending instructor.
     *
     * <p>Requires a verified email address first. Approving an unverified
     * account would grant authoring rights to an address nobody has proven
     * belongs to the applicant — and an instructor is precisely the role where
     * impersonation does the most damage, since their items decide what other
     * people are judged on.
     */
    @Transactional
    public void approveInstructor(Long userId, Long approvedByAdminId) {
        User user = users.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (!user.hasRole(RoleName.INSTRUCTOR)) {
            throw new ValidationException("NOT_AN_INSTRUCTOR",
                    "That account has not applied for instructor access.");
        }
        if (user.getStatus() != AccountStatus.PENDING) {
            throw new ValidationException("NOT_PENDING",
                    "That account is not awaiting approval.");
        }
        if (!user.isEmailVerified()) {
            throw new ValidationException("EMAIL_NOT_VERIFIED",
                    "The applicant must confirm their email address before approval.");
        }

        user.setStatus(AccountStatus.ACTIVE);
        log.info("Instructor {} approved by admin {}", userId, approvedByAdminId);
        auditLogger.record("INSTRUCTOR_APPROVED", "USER", userId,
                statusJson(AccountStatus.PENDING), statusJson(AccountStatus.ACTIVE), null);

        events.publishEvent(new InstructorApproved(
                user.getId(), user.getEmail(), user.getFullName()));
    }

    @Transactional
    public void rejectInstructor(Long userId, String reason, Long adminId) {
        User user = users.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (user.getStatus() != AccountStatus.PENDING) {
            throw new ValidationException("NOT_PENDING", "That account is not awaiting approval.");
        }

        // Suspended rather than deleted. The application is a record, and an
        // applicant who reapplies should not silently become a second account.
        user.setStatus(AccountStatus.SUSPENDED);
        log.info("Instructor application {} rejected by admin {}: {}", userId, adminId, reason);
        auditLogger.record("INSTRUCTOR_REJECTED", "USER", userId,
                statusJson(AccountStatus.PENDING), statusJson(AccountStatus.SUSPENDED), reason);
    }

    /**
     * Suspends an account, ending every session immediately.
     *
     * <p>Both halves are required and neither is sufficient alone. Flipping the
     * status stops future logins but leaves existing tokens working until they
     * expire — so a suspended instructor would keep full access for another
     * fifteen minutes, which is not a suspension in any sense a user would
     * recognise. Revoking sessions without the status change lets them simply
     * sign in again.
     */
    @Transactional
    public void suspend(Long userId, String reason, Long adminId) {
        User user = users.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (user.hasRole(RoleName.ADMIN)) {
            // Otherwise the last administrator can lock the platform out of
            // itself, and no path back exists short of database surgery.
            throw new ValidationException("CANNOT_SUSPEND_ADMIN",
                    "Administrator accounts cannot be suspended through the API.");
        }

        AccountStatus previousStatus = user.getStatus();
        user.setStatus(AccountStatus.SUSPENDED);
        sessionRevoker.revokeAllSessions(userId, RefreshToken.RevocationReason.ADMIN_REVOKED);

        log.warn("Account {} suspended by admin {}: {}", userId, adminId, reason);
        auditLogger.record("USER_SUSPENDED", "USER", userId,
                statusJson(previousStatus), statusJson(AccountStatus.SUSPENDED), reason);
        events.publishEvent(new AccountSuspended(user.getId(), user.getEmail(), reason));
    }

    @Transactional
    public void reactivate(Long userId, Long adminId) {
        User user = users.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));
        user.setStatus(AccountStatus.ACTIVE);
        log.info("Account {} reactivated by admin {}", userId, adminId);
        auditLogger.record("USER_REACTIVATED", "USER", userId,
                statusJson(AccountStatus.SUSPENDED), statusJson(AccountStatus.ACTIVE), null);
    }

    private String statusJson(AccountStatus status) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", status.name());
        return node.toString();
    }

    @Transactional(readOnly = true)
    public List<AdminUserDtos.UserSummary> listUsers() {
        return users.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(user -> new AdminUserDtos.UserSummary(
                        user.getId(), user.getEmail(), user.getFullName(),
                        user.getStatus().name(), user.isEmailVerified(),
                        user.getRoles().stream().map(role -> role.getName().name()).toList(),
                        user.getCreatedAt(), user.getLastLoginAt()))
                .toList();
    }
}
