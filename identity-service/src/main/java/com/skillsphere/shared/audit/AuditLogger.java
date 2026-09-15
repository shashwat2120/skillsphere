package com.skillsphere.shared.audit;

import com.skillsphere.shared.security.ClientIp;
import com.skillsphere.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Records one administrative action, in the same transaction as the action
 * it describes.
 *
 * <p>Deliberately synchronous rather than event-published. An audit trail
 * that could be lost between the action committing and an async listener
 * running is not a trustworthy audit trail — "the log is never deleted" (see
 * the {@code admin_audit_log} migration) only means something if the entry
 * was guaranteed to be written in the first place. Every module that takes an
 * admin action calls this directly, inside its own {@code @Transactional}
 * method, so the log entry and the action it describes commit or roll back
 * together.
 *
 * <p>The caller supplies only what it actually knows — the action, what it
 * touched, and the before/after state. The acting admin's id and the client
 * address are filled in here from the current request, so every call site is
 * spared re-deriving them and cannot accidentally attribute an action to the
 * wrong admin.
 *
 * <p><b>Silently no-ops outside a real, authenticated HTTP request</b> —
 * checked before touching either {@link CurrentUser} or the request-scoped
 * {@link HttpServletRequest} proxy, rather than letting either throw. Every
 * genuine admin action reaches this class through a controller guarded by
 * {@code @PreAuthorize("hasRole('ADMIN')")}, so it always has both an
 * authenticated principal and a live request — production coverage is
 * complete. What this guard is actually for is service-layer tests such as
 * {@code SkillGraphTest}, which call {@code SkillGraphService} directly to
 * test graph correctness and rightly do not simulate an HTTP admin session;
 * without it, adding this call would have forced every such test to fake an
 * authenticated request just to exercise unrelated business logic. Skipping
 * the entry is the honest choice here — there is no real admin to attribute
 * it to, and fabricating one would be a worse audit trail than having none.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogger {

    private final AdminAuditLogRepository logs;
    private final HttpServletRequest request;

    @Transactional
    public void record(String action, String targetType, Long targetId,
                        String beforeStateJson, String afterStateJson, String reason) {
        if (RequestContextHolder.getRequestAttributes() == null) {
            log.debug("Skipping audit entry for {} {} — no HTTP request in context", action, targetType);
            return;
        }
        var caller = CurrentUser.get();
        if (caller.isEmpty()) {
            log.debug("Skipping audit entry for {} {} — no authenticated caller in context", action, targetType);
            return;
        }

        AdminAuditLog entry = new AdminAuditLog(
                caller.get().id(), action, targetType, targetId,
                beforeStateJson, afterStateJson, ClientIp.from(request), reason);
        logs.save(entry);
    }
}
