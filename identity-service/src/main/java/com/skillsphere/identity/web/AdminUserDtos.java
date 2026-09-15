package com.skillsphere.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record PendingInstructor(
            Long id,
            String email,
            String fullName,
            boolean emailVerified,
            Instant appliedAt) {
    }

    public record UserSummary(
            Long id,
            String email,
            String fullName,
            String status,
            boolean emailVerified,
            List<String> roles,
            Instant createdAt,
            Instant lastLoginAt) {
    }

    /**
     * A reason is mandatory for anything that removes access.
     *
     * <p>Not bureaucracy: the reason lands in the audit log and in the message to
     * the affected person. An unexplained suspension is unappealable, and months
     * later nobody — including the administrator who did it — can reconstruct why.
     */
    public record ReasonRequest(
            @NotBlank @Size(max = 500) String reason) {
    }
}
