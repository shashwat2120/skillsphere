-- =============================================================================
-- V3 — Local read-model mirror of assessment-service's arena-relevant items
--
-- ArenaItemSourceShim (selectItemsForSkill, findById, score) used to query
-- the shared items/item_options tables directly. Unlike skills_mirror,
-- this one is not seeded with literal INSERT statements in this migration
-- — 52 items and ~210 options do not belong hand-transcribed into SQL, and
-- assessment-service's own item bank seed already exists as the source of
-- truth. Bootstrapped once from assessment_db via pg_dump/restore at
-- deploy time instead (the same one-time-copy treatment every other
-- service's genuinely-owned table gets), then kept current going forward
-- by ItemCatalogEventListener consuming item-catalog-events — only
-- ACTIVE/RETIRED items ever arrive there (see ItemCatalogChanged's own
-- class comment for why drafts are excluded), which is exactly the subset
-- {@link ArenaItemSourceShim#selectItemsForSkill} ever draws from anyway.
--
-- is_correct is mirrored deliberately — see ItemCatalogChanged's own class
-- comment for why the answer key travels with the event: ArenaItemSourceShim
-- #score grades every live-arena answer server-side in this process, and
-- that key is never re-exposed through this service's own API.
-- =============================================================================

CREATE TABLE items_mirror (
    id         BIGINT PRIMARY KEY,
    skill_id   BIGINT NOT NULL,
    stem       TEXT   NOT NULL,
    status     VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_items_mirror_skill_active ON items_mirror (skill_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE items_mirror IS
'Read-only local copy of assessment-service''s items table, restricted in practice to items that have ever been ACTIVE or RETIRED (draft items never publish an event). Kept current via Kafka.';

CREATE TABLE item_options_mirror (
    id       BIGINT PRIMARY KEY,
    item_id  BIGINT NOT NULL,
    text     TEXT   NOT NULL,
    correct  BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL DEFAULT 0,

    CONSTRAINT fk_iom_item FOREIGN KEY (item_id) REFERENCES items_mirror (id) ON DELETE CASCADE
);
CREATE INDEX idx_iom_item ON item_options_mirror (item_id, position);

COMMENT ON TABLE item_options_mirror IS
'Read-only local copy of assessment-service''s item_options table, including the answer key (correct) — see this file''s header comment for why that is safe here.';
