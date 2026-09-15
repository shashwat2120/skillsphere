-- =============================================================================
-- V11 — IP address columns from INET to VARCHAR(45)
--
-- PostgreSQL's INET is the better column type in isolation: it validates
-- addresses and supports subnet operators, which would be genuinely useful for
-- "show me every failed login from this /24".
--
-- It is dropped anyway because JPA has no portable mapping for it. Keeping INET
-- would mean a custom Hibernate type on every entity touching an IP, and that
-- friction is paid on each of the five tables below for an operator set we do
-- not currently use. If subnet querying is needed later, the analytics side can
-- cast on read.
--
-- VARCHAR(45) fits the longest possible address: an IPv4-mapped IPv6 form such
-- as 0000:0000:0000:0000:0000:ffff:255.255.255.255.
-- =============================================================================

ALTER TABLE user_devices    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
ALTER TABLE refresh_tokens  ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
ALTER TABLE login_attempts  ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
ALTER TABLE auth_audit_log  ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;
ALTER TABLE admin_audit_log ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::text;

COMMENT ON COLUMN refresh_tokens.ip_address IS
    'Client address at issue time. Recorded for the session manager and for spotting a refresh token surfacing from an unexpected location.';
