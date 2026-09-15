package com.skillsphere.shared.audit;

import com.skillsphere.shared.security.ClientIp;
import com.skillsphere.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Instant;

/**
 * Records one administrative action, in the same local transaction as the
 * action it describes.
 *
 * <p>Publishes {@link AdminActionRecorded} into Modulith's transactional
 * outbox rather than writing {@code admin_audit_log} directly, because this
 * process no longer has that table — it moved to identity-service's own
 * database as part of the database-per-service split (see that record's
 * class comment for why identity is the table's new sole owner). This is
 * not a weaker guarantee than the direct write it replaces: the outbox
 * write happens in the same local transaction as the action being audited,
 * so an action that rolls back still produces no event, exactly as before —
 * it is the same commit-coupled guarantee {@code AnalyticsEventBridge}
 * already gives {@code ResponseRecorded}, and Modulith's own retry-on-
 * restart semantics are stronger against process failure than the single-
 * process synchronous write ever was. What is genuinely new is that the
 * entry lands in identity-service's database on a short delay rather than
 * atomically with this transaction's commit — a real trade, but one every
 * other cross-service fact in this system already makes, and treating audit
 * specifically as needing a stronger guarantee than the mastery updates
 * feeding risk scoring would be an arbitrary exception, not a principled
 * one.
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

    private final ApplicationEventPublisher events;
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

        events.publishEvent(new AdminActionRecorded(
                caller.get().id(), action, targetType, targetId,
                beforeStateJson, afterStateJson, ClientIp.from(request), reason, Instant.now()));
    }
}
