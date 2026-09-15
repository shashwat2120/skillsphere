-- =============================================================================
-- V18 — identity's tables leave the shared database
--
-- identity-service now owns its own database (identity_db) — see
-- identity-service/src/main/resources/db/migration/V1__identity.sql, which
-- recreates every table below plus admin_audit_log (moved there in full,
-- see that migration's comment) against a real physical copy of the data
-- that lived here. This migration is what makes that move real in the
-- database every other still-shared-schema service actually runs against:
-- it removes the tables, which Postgres will not allow while a foreign key
-- anywhere still points at them.
--
-- DROP TABLE ... CASCADE is used deliberately over hand-listing every
-- ALTER TABLE ... DROP CONSTRAINT: cascading a DROP TABLE only removes
-- dependent objects (the foreign key constraints defined on OTHER tables
-- that reference this one) — it does not touch the referencing tables or
-- their rows. That is exactly the shape of this change: a plain user_id
-- column stays on every table below with data intact, it simply stops being
-- database-enforced. This is a deliberate trade, not an oversight — see the
-- next comment block.
--
-- WHY THE DROPPED user_id/instructor_id/etc. COLUMNS GET NO REPLACEMENT
-- CHECK. Every one of these columns is already a plain, unrelated @Column in
-- its owning service's JPA entity (none of them ever modelled a @ManyToOne
-- to a User entity that does not exist in that service) — confirmed before
-- writing this migration, so this is a verified fact, not an assumption.
-- Postgres could no longer validate these values even if left in place,
-- since identity_db is a different physical database and cross-database
-- foreign keys do not exist without postgres_fdw. Two kinds of writer touch
-- these columns:
--   - self-referencing writes (a learner's own assessment, their own
--     enrollment): the id comes straight out of an already-verified JWT, so
--     a request that reaches the write path has already proven the user
--     exists by successfully authenticating as them.
--   - admin/instructor-acting-on-behalf-of writes (author_id, verified_by,
--     acknowledged_by, intervened_by, reviewer_id): gated by
--     @PreAuthorize("hasRole(...)") already; a bad id here is a data-quality
--     edge case (an admin fat-fingering something that isn't exposed as a
--     picker in the UI), not a security one. Adding a synchronous
--     cross-service existence check on every one of these writes would
--     reintroduce, for a rare edge case, exactly the kind of runtime
--     coupling between services that database-per-service exists to
--     remove. Not doing that is the point of this migration, not a gap in
--     it.
--
-- Four more tables come out in the same migration for an unrelated reason:
-- instructor_applications, notifications, notification_preferences and
-- platform_settings all carry a foreign key into users(id), so they are
-- forced into scope by users leaving — and a repo-wide search turned up
-- zero Java code anywhere across all eight services that reads or writes
-- any of the four. They were never wired up after being designed in V9;
-- keeping them would mean carrying dead schema that a reviewer could read
-- as a feature that exists. Dropping them is more honest than finding them
-- a home. (Real seed data existed only in platform_settings — 9 rows of
-- runtime-tunable defaults that were superseded by Spring Cloud Config once
-- Sprint 6 began; nothing ever read them back out. gamification's own
-- five foreign keys into users(id) are NOT touched by this cleanup — that
-- module is a genuinely separate, already-flagged gap (no owning service
-- since the Sprint 6 split, tracked in the build checklist), not part of
-- this migration's scope; CASCADE handles its FK constraints exactly like
-- every other referencing table, but its tables and rows stay untouched.)
-- =============================================================================

DROP TABLE IF EXISTS
    user_roles,
    mfa_recovery_codes,
    mfa_totp,
    webauthn_credentials,
    password_reset_tokens,
    email_verification_tokens,
    login_attempts,
    auth_audit_log,
    admin_audit_log,
    refresh_tokens,
    user_devices,
    instructor_applications,
    notifications,
    notification_preferences,
    platform_settings,
    roles,
    users
CASCADE;
