-- =============================================================================
-- V3 — Admin & Platform (SKILLSPHERE.md §10.1 section I)
--
-- Two tables the Phase 1 audit found missing: instructor_applications backs
-- UserAdminService's approve/reject flow with an actual record instead of
-- just a status flip on users.status, and platform_settings gives admins a
-- generic key/value store for platform-wide configuration.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- instructor_applications — the approval queue, as a durable record
-- -----------------------------------------------------------------------------
CREATE TABLE instructor_applications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    applied_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_by   BIGINT,
    reviewed_at   TIMESTAMPTZ,
    notes         TEXT,

    CONSTRAINT fk_instr_app_user     FOREIGN KEY (user_id)     REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_instr_app_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_instr_app_status   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

COMMENT ON TABLE instructor_applications IS 'One row per instructor registration. UserAdminService.approveInstructor/rejectInstructor flips users.status AND updates the matching row here, so the decision — who reviewed it, when, and why — survives independently of the account''s current status.';

CREATE INDEX idx_instr_app_user   ON instructor_applications (user_id, applied_at DESC);
CREATE INDEX idx_instr_app_status ON instructor_applications (status) WHERE status = 'PENDING';

-- -----------------------------------------------------------------------------
-- platform_settings — generic admin-editable configuration
-- -----------------------------------------------------------------------------
CREATE TABLE platform_settings (
    key         VARCHAR(100) PRIMARY KEY,
    value       JSONB        NOT NULL,
    updated_by  BIGINT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_platform_settings_updater FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

COMMENT ON TABLE platform_settings IS 'Admin-editable platform configuration as arbitrary JSON, one row per key. Read/write via PlatformSettingsController; every write is also recorded in admin_audit_log.';
