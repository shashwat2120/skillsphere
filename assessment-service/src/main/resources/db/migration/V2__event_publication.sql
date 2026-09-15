-- =============================================================================
-- V2 — Spring Modulith's transactional outbox
--
-- AnalyticsEventBridge (ResponseRecorded -> assessment-events) and
-- AdminAuditKafkaBridge (AdminActionRecorded -> admin-audit-events) both
-- listen via @ApplicationModuleListener, backed by spring-modulith-starter-
-- jpa: a domain event and the write that raised it commit in the same
-- local transaction, recorded here, so an event can never be lost after a
-- successful write. TEXT, not VARCHAR(255) — see identity-service's own
-- copy of this migration for why: a monolith migration that assumed 255
-- characters was always enough for a serialized event broke the first time
-- one carried real JSON state.
-- =============================================================================

CREATE TABLE IF NOT EXISTS event_publication (
    id                     UUID         NOT NULL,
    listener_id            VARCHAR(255) NOT NULL,
    event_type             VARCHAR(255) NOT NULL,
    serialized_event       TEXT         NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    completion_attempts    INTEGER      NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(255),

    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_event_publication_incomplete
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;

CREATE INDEX IF NOT EXISTS idx_event_publication_completion
    ON event_publication (completion_date);

COMMENT ON TABLE event_publication IS
    'Spring Modulith outbox. Rows with a null completion_date are events whose listener has not yet succeeded, and are retried on restart.';
