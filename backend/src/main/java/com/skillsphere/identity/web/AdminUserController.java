package com.skillsphere.identity.web;

import com.skillsphere.identity.internal.UserAdminService;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account administration.
 *
 * <p>This is the other half of the instructor gate. Registration puts an
 * instructor into {@code PENDING} and blocks sign-in; without these endpoints
 * that is a wall rather than a gate, and applicants wait forever.
 *
 * <p>Every action here is recorded with the acting administrator's id. Admin
 * actions are the ones with the widest blast radius and the least oversight, so
 * "who did this and why" has to be answerable afterwards.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "User administration", description = "Instructor approval and account moderation")
public class AdminUserController {

    private final UserAdminService userAdmin;

    @GetMapping("/pending-instructors")
    @Operation(summary = "Instructors awaiting approval",
            description = "Applicants who registered as instructors and cannot sign in until approved.")
    public List<AdminUserDtos.PendingInstructor> pendingInstructors() {
        return userAdmin.listPendingInstructors();
    }

    @PostMapping("/{id}/approve-instructor")
    @Operation(summary = "Grant instructor access",
            description = "Requires a confirmed email address. Approving an unverified account "
                    + "would grant authoring rights to an address nobody has proven belongs to "
                    + "the applicant.")
    public ResponseEntity<Void> approveInstructor(@PathVariable Long id) {
        userAdmin.approveInstructor(id, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject-instructor")
    @Operation(summary = "Decline an instructor application")
    public ResponseEntity<Void> rejectInstructor(@PathVariable Long id,
                                                 @Valid @RequestBody AdminUserDtos.ReasonRequest request) {
        userAdmin.rejectInstructor(id, request.reason(), CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an account",
            description = "Takes effect immediately: the status change blocks future logins and "
                    + "every existing session is revoked, so access ends now rather than when the "
                    + "current token happens to expire.")
    public ResponseEntity<Void> suspend(@PathVariable Long id,
                                        @Valid @RequestBody AdminUserDtos.ReasonRequest request) {
        userAdmin.suspend(id, request.reason(), CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Restore a suspended account")
    public ResponseEntity<Void> reactivate(@PathVariable Long id) {
        userAdmin.reactivate(id, CurrentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "All accounts")
    public List<AdminUserDtos.UserSummary> list() {
        return userAdmin.listUsers();
    }
}
