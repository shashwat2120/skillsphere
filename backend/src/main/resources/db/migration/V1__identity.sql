-- =============================================================================
-- V1 — Identity & Security
-- Users, roles, tokens, MFA, passkeys, audit.
-- Everything else in the schema references users, so this migration runs first.
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
    status              VARCHAR(20)   NOT NULL DEFAULT 'active',
    email_verified_at   TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT uq_users_email       UNIQUE (email),
    CONSTRAINT ck_users_status      CHECK (status IN ('active', 'pending', 'suspended')),
    -- password_hash is nullable so passkey-only and OAuth-only accounts are possible
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email))
);

COMMENT ON TABLE  users IS 'Platform accounts. password_hash holds an Argon2id digest and is null for passkey-only or OAuth-only users.';
COMMENT ON COLUMN users.status IS 'active = usable; pending = awaiting admin approval (instructors); suspended = blocked, sessions terminated on next request.';

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

    CONSTRAINT uq_roles_name CHECK (name IN ('learner', 'instructor', 'admin', 'employer'))
);
CREATE UNIQUE INDEX uq_roles_name_idx ON roles (name);

INSERT INTO roles (name, description) VALUES
    ('learner',    'Learns through paths, takes assessments, submits projects and defends them'),
    ('instructor', 'Authors content and items, reviews submissions, runs live sessions'),
    ('admin',      'Manages the skill graph, career catalogue, users and platform settings'),
    ('employer',   'Views shared skill passports and their supporting evidence');

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
    ip_address    INET,
    trusted       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

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
    ip_address       INET,
    user_agent       VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_token_hash  UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user        FOREIGN KEY (user_id)        REFERENCES users (id)          ON DELETE CASCADE,
    CONSTRAINT fk_refresh_device      FOREIGN KEY (device_id)      REFERENCES user_devices (id)   ON DELETE SET NULL,
    CONSTRAINT fk_refresh_replaced    FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

COMMENT ON TABLE  refresh_tokens IS 'Only the SHA-256 hash of a refresh token is stored. replaced_by_id forms a rotation chain: presenting an already-rotated token indicates theft, and the whole chain is revoked.';
COMMENT ON COLUMN refresh_tokens.revoked_reason IS 'logout | rotated | reuse_detected | password_changed | admin_revoked';

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
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    ip_address    INET,
    success       BOOLEAN      NOT NULL,
    failure_reason VARCHAR(50),
    attempted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
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
    ip_address  INET,
    user_agent  VARCHAR(500),
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_auth_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
COMMENT ON COLUMN auth_audit_log.event_type IS 'login | logout | password_changed | mfa_enabled | mfa_disabled | passkey_added | new_device | token_reuse_detected | account_locked';

CREATE INDEX idx_auth_audit_user_time ON auth_audit_log (user_id, created_at DESC);
CREATE INDEX idx_auth_audit_type      ON auth_audit_log (event_type, created_at DESC);
