-- =============================================================================
-- V1 — Identity & Security, this service's own database
--
-- This is the first migration written directly against identity_db rather
-- than inherited from the monolith's shared V1__identity.sql. It represents
-- the FINAL shape those tables had already reached in the shared database —
-- uppercase enum literals (monolith V1 itself), ip_address as VARCHAR(45)
-- rather than INET (monolith V11) — collapsed into one clean "service birth"
-- migration rather than replayed as a multi-file history that never actually
-- applied to this database. admin_audit_log moves here too: it used to live
-- in the shared database with both identity-service and assessment-service
-- writing to it directly, which only worked because they shared one
-- database. Now that they don't, assessment-service's skill-catalog audit
-- entries arrive over Kafka instead (see AdminAuditEventListener) — this is
-- the one place that still writes the table directly, for its own actions.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255),
    full_name           VARCHAR(150)  NOT NULL,
    avatar_url          VARCHAR(500),
    headline            VARCHAR(200),
    bio                 TEXT,
    timezone            VARCHAR(64)   NOT NULL DEFAULT 'UTC',
    locale              VARCHAR(10)   NOT NULL DEFAULT 'en',
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    email_verified_at   TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT uq_users_email       UNIQUE (email),
    CONSTRAINT ck_users_status      CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED')),
    -- password_hash is nullable so passkey-only and OAuth-only accounts are possible
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

COMMENT ON TABLE  users IS 'Platform accounts. password_hash holds an Argon2id digest and is null for passkey-only or OAuth-only users.';
COMMENT ON COLUMN users.status IS 'ACTIVE = usable; PENDING = awaiting admin approval (instructors); SUSPENDED = blocked, sessions terminated on next request. Enum literals are uppercase throughout the schema to match JPA @Enumerated(STRING).';

CREATE INDEX idx_users_status      ON users (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_created     ON users (created_at DESC);

-- -----------------------------------------------------------------------------
-- roles / user_roles
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(30)  NOT NULL,
    description  VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_roles_name CHECK (name IN ('LEARNER', 'INSTRUCTOR', 'ADMIN', 'EMPLOYER'))
);
CREATE UNIQUE INDEX uq_roles_name_idx ON roles (name);

INSERT INTO roles (name, description) VALUES
    ('LEARNER',    'Learns through paths, takes assessments, submits projects and defends them'),
    ('INSTRUCTOR', 'Authors content and items, reviews submissions, runs live sessions'),
    ('ADMIN',      'Manages the skill graph, career catalogue, users and platform settings'),
    ('EMPLOYER',   'Views shared skill passports and their supporting evidence');

CREATE TABLE user_roles (
    user_id     BIGINT      NOT NULL,
    role_id     BIGINT      NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by  BIGINT,

    CONSTRAINT pk_user_roles         PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user    FOREIGN KEY (user_id)    REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role    FOREIGN KEY (role_id)    REFERENCES roles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_granter FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_user_roles_role ON user_roles (role_id);

-- -----------------------------------------------------------------------------
-- user_devices — powers the "active sessions" screen
-- -----------------------------------------------------------------------------
CREATE TABLE user_devices (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    device_label  VARCHAR(150),
    user_agent    VARCHAR(500),
    ip_address    VARCHAR(45),
    trusted       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_devices_user ON user_devices (user_id, last_seen_at DESC);

-- -----------------------------------------------------------------------------
-- refresh_tokens — rotation with reuse detection
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    device_id        BIGINT,
    token_hash       VARCHAR(255) NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    revoked_at       TIMESTAMPTZ,
    revoked_reason   VARCHAR(50),
    replaced_by_id   BIGINT,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_token_hash  UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user        FOREIGN KEY (user_id)        REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_refresh_device      FOREIGN KEY (device_id)      REFERENCES user_devices (id)   ON DELETE SET NULL,
    CONSTRAINT fk_refresh_replaced    FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

COMMENT ON TABLE  refresh_tokens IS 'Only the SHA-256 hash of a refresh token is stored. replaced_by_id forms a rotation chain: presenting an already-rotated token indicates theft, and the whole chain is revoked.';
COMMENT ON COLUMN refresh_tokens.revoked_reason IS 'LOGOUT | ROTATED | REUSE_DETECTED | PASSWORD_CHANGED | ADMIN_REVOKED';
COMMENT ON COLUMN refresh_tokens.ip_address IS 'Client address at issue time. Recorded for the session manager and for spotting a refresh token surfacing from an unexpected location.';

CREATE INDEX idx_refresh_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_expires     ON refresh_tokens (expires_at);

-- -----------------------------------------------------------------------------
-- MFA — TOTP, recovery codes, WebAuthn passkeys
-- -----------------------------------------------------------------------------
CREATE TABLE mfa_totp (
    user_id           BIGINT      PRIMARY KEY,
    secret_encrypted  VARCHAR(500) NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT FALSE,
    confirmed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_mfa_totp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
COMMENT ON COLUMN mfa_totp.secret_encrypted IS 'TOTP shared secret, encrypted at rest with the application key — never stored in plaintext.';

CREATE TABLE mfa_recovery_codes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    code_hash  VARCHAR(255) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_mfa_recovery_user ON mfa_recovery_codes (user_id) WHERE used_at IS NULL;

CREATE TABLE webauthn_credentials (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    credential_id   VARCHAR(500) NOT NULL,
    public_key      BYTEA        NOT NULL,
    sign_count      BIGINT       NOT NULL DEFAULT 0,
    transports      VARCHAR(100),
    device_label    VARCHAR(150),
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_webauthn_credential UNIQUE (credential_id),
    CONSTRAINT fk_webauthn_user       FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_webauthn_user ON webauthn_credentials (user_id);

-- -----------------------------------------------------------------------------
-- One-time tokens
-- -----------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_pwd_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_pwd_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_pwd_reset_user ON password_reset_tokens (user_id) WHERE used_at IS NULL;

CREATE TABLE email_verification_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_email_verify_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verify_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- login_attempts — lockout and rate limiting
-- -----------------------------------------------------------------------------
CREATE TABLE login_attempts (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    ip_address     VARCHAR(45),
    success        BOOLEAN      NOT NULL,
    failure_reason VARCHAR(50),
    attempted_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE login_attempts IS 'Feeds per-account and per-IP lockout. Recorded by email string rather than user_id so attempts against non-existent accounts are also counted.';

CREATE INDEX idx_login_email_time ON login_attempts (email, attempted_at DESC);
CREATE INDEX idx_login_ip_time    ON login_attempts (ip_address, attempted_at DESC);

-- -----------------------------------------------------------------------------
-- auth_audit_log
-- -----------------------------------------------------------------------------
CREATE TABLE auth_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    event_type  VARCHAR(50)  NOT NULL,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(500),
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_auth_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
COMMENT ON COLUMN auth_audit_log.event_type IS 'login | logout | password_changed | mfa_enabled | mfa_disabled | passkey_added | new_device | token_reuse_detected | account_locked';

CREATE INDEX idx_auth_audit_user_time ON auth_audit_log (user_id, created_at DESC);
CREATE INDEX idx_auth_audit_type      ON auth_audit_log (event_type, created_at DESC);

-- -----------------------------------------------------------------------------
-- admin_audit_log
--
-- Moved here in full as part of the database-per-service split. Previously
-- both identity-service and assessment-service wrote to this table directly
-- against the shared database — a write collision that only worked by
-- accident of sharing one schema. Now identity-service is the sole owner:
-- its own admin actions write here directly (AuditLogger, unchanged), and
-- assessment-service's skill-catalog admin actions arrive as Kafka events
-- consumed by AdminAuditEventListener instead of a second direct writer.
-- -----------------------------------------------------------------------------
CREATE TABLE admin_audit_log (
    id           BIGSERIAL PRIMARY KEY,
    admin_id     BIGINT       NOT NULL,
    action       VARCHAR(60)  NOT NULL,
    target_type  VARCHAR(40)  NOT NULL,
    target_id    BIGINT,
    before_state JSONB,
    after_state  JSONB,
    ip_address   VARCHAR(45),
    reason       VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_aal_admin FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE RESTRICT
);
COMMENT ON TABLE admin_audit_log IS 'Admins hold destructive power — suspending accounts, rewriting the skill graph, changing career requirements. Every such action is recorded with its before and after state, and the log is never deleted.';

CREATE INDEX idx_aal_admin_time ON admin_audit_log (admin_id, created_at DESC);
CREATE INDEX idx_aal_target     ON admin_audit_log (target_type, target_id, created_at DESC);
