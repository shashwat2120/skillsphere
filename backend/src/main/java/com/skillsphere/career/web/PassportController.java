package com.skillsphere.career.web;

import com.skillsphere.career.internal.PassportService;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The skill passport — a learner's own live view, and the frozen, public
 * view a share link opens.
 *
 * <p>Split across two path prefixes rather than one, because they have
 * fundamentally different trust levels: {@code /api/passport/**} requires
 * the caller's own identity and never leaves the security filter chain's
 * authenticated zone; {@code /api/public/passports/**} is reachable by
 * anyone holding the token and nothing else — matching the pre-declared
 * {@code permitAll()} pattern in {@code SecurityConfig} rather than
 * inventing a new one.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Skill Passport", description = "Evidence-backed skill summary, live and shared")
public class PassportController {

    private final PassportService passportService;

    @GetMapping("/api/passport")
    @Operation(summary = "The signed-in learner's live passport",
            description = "Recomputed from current data on every call — this view must never lag behind "
                    + "a diagnostic just answered or a viva just passed.")
    public PassportService.PassportView current() {
        return passportService.current(CurrentUser.requireId());
    }

    @PostMapping("/api/passport/share")
    @Operation(summary = "Freeze the current passport into a shareable link",
            description = "careerRoleId is optional — when given, the snapshot also carries a readiness "
                    + "score against that role, frozen at the moment of sharing like everything else on it.")
    public PassportService.SnapshotView share(
            @RequestParam(required = false) Long careerRoleId) {
        return passportService.share(CurrentUser.requireId(), careerRoleId);
    }

    @GetMapping("/api/public/passports/{token}")
    @Operation(summary = "The public, no-auth view of a shared passport",
            description = "Anyone holding the token can open this — that is the point of a share link. "
                    + "Serves only what was frozen at share time.")
    public PassportService.SnapshotView publicView(@PathVariable String token) {
        return passportService.publicView(token);
    }
}
