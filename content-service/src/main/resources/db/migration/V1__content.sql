-- =============================================================================
-- V1 — Content, this service's own database
--
-- Same approach as every other service's own V1: the final shape these 6
-- tables had already reached (monolith V3 + V12's uppercase literals)
-- collapsed into one clean "service birth" migration.
--
-- Foreign keys NOT reproduced — same reasoning as assessment-service's own
-- V1 header comment:
--   - instructor_id / user_id -> users(id): identity-service's database now.
--   - lesson_skills.skill_id -> skills(id): assessment-service's database
--     now. This service keeps a small local mirror instead (see V2).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- course_categories
-- -----------------------------------------------------------------------------
CREATE TABLE course_categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_course_category_slug UNIQUE (slug)
);

-- -----------------------------------------------------------------------------
-- courses
-- -----------------------------------------------------------------------------
CREATE TABLE courses (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    slug           VARCHAR(220) NOT NULL,
    subtitle       VARCHAR(300),
    description    TEXT,
    thumbnail_url  VARCHAR(500),
    -- No foreign key — see this file's header comment.
    instructor_id  BIGINT       NOT NULL,
    category_id    BIGINT,
    level_band     VARCHAR(20)  NOT NULL DEFAULT 'INTERMEDIATE',
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    est_minutes    INT          NOT NULL DEFAULT 0,
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT uq_courses_slug      UNIQUE (slug),
    CONSTRAINT fk_courses_category   FOREIGN KEY (category_id)   REFERENCES course_categories (id) ON DELETE SET NULL,
    CONSTRAINT ck_courses_status     CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_courses_band       CHECK (level_band IN ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);

CREATE INDEX idx_courses_instructor ON courses (instructor_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_courses_published  ON courses (status, published_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_courses_category   ON courses (category_id);

-- -----------------------------------------------------------------------------
-- course_modules
-- -----------------------------------------------------------------------------
CREATE TABLE course_modules (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL,
    summary     VARCHAR(500),
    position    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT fk_modules_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT uq_module_position UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_modules_course ON course_modules (course_id, position);

-- -----------------------------------------------------------------------------
-- lessons
-- -----------------------------------------------------------------------------
CREATE TABLE lessons (
    id               BIGSERIAL PRIMARY KEY,
    module_id        BIGINT       NOT NULL,
    title            VARCHAR(200) NOT NULL,
    type             VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    content          TEXT,
    video_url        VARCHAR(500),
    resource_url     VARCHAR(500),
    duration_seconds INT          NOT NULL DEFAULT 0,
    position         INT          NOT NULL DEFAULT 0,
    is_preview       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id) REFERENCES course_modules (id) ON DELETE CASCADE,
    CONSTRAINT ck_lessons_type   CHECK (type IN ('TEXT', 'VIDEO', 'RESOURCE', 'INTERACTIVE'))
);
CREATE INDEX idx_lessons_module ON lessons (module_id, position);

-- -----------------------------------------------------------------------------
-- lesson_skills — the join that makes content serve the skill graph
-- -----------------------------------------------------------------------------
CREATE TABLE lesson_skills (
    lesson_id  BIGINT       NOT NULL,
    -- No foreign key — see this file's header comment.
    skill_id   BIGINT       NOT NULL,
    weight     NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_lesson_skills    PRIMARY KEY (lesson_id, skill_id),
    CONSTRAINT fk_lesson_skills_l  FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT ck_lesson_skills_w  CHECK (weight > 0 AND weight <= 1)
);

COMMENT ON TABLE lesson_skills IS 'Maps teaching content onto the skill graph. Without this join the path engine cannot recommend a lesson for a skill gap.';

CREATE INDEX idx_lesson_skills_skill ON lesson_skills (skill_id);

-- -----------------------------------------------------------------------------
-- enrollments
-- -----------------------------------------------------------------------------
CREATE TABLE enrollments (
    id           BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id      BIGINT      NOT NULL,
    course_id    BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source       VARCHAR(30) NOT NULL DEFAULT 'SELF',
    progress_pct NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    enrolled_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    last_active_at TIMESTAMPTZ,

    CONSTRAINT uq_enrollment      UNIQUE (user_id, course_id),
    CONSTRAINT fk_enroll_course   FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_enroll_status   CHECK (status IN ('ACTIVE', 'COMPLETED', 'DROPPED')),
    CONSTRAINT ck_enroll_source   CHECK (source IN ('SELF', 'PATH', 'ASSIGNED')),
    CONSTRAINT ck_enroll_progress CHECK (progress_pct >= 0 AND progress_pct <= 100)
);

COMMENT ON COLUMN enrollments.source IS 'SELF = learner browsed and enrolled; PATH = the path engine placed them here; ASSIGNED = instructor or admin.';

CREATE INDEX idx_enroll_user   ON enrollments (user_id, status);
CREATE INDEX idx_enroll_course ON enrollments (course_id, status);
CREATE INDEX idx_enroll_stale  ON enrollments (last_active_at) WHERE status = 'ACTIVE';

-- -----------------------------------------------------------------------------
-- lesson_progress
-- -----------------------------------------------------------------------------
CREATE TABLE lesson_progress (
    id              BIGSERIAL PRIMARY KEY,
    -- No foreign key — see this file's header comment.
    user_id         BIGINT      NOT NULL,
    lesson_id       BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    seconds_watched INT         NOT NULL DEFAULT 0,
    last_position   INT         NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,

    CONSTRAINT uq_lesson_progress    UNIQUE (user_id, lesson_id),
    CONSTRAINT fk_lprog_lesson       FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT ck_lprog_status       CHECK (status IN ('STARTED', 'COMPLETED'))
);
CREATE INDEX idx_lprog_user ON lesson_progress (user_id, status);
