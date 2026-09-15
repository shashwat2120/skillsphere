-- =============================================================================
-- V20 — the last six services' tables leave the shared database
--
-- Same move as V18 (identity), applied to every table that moved to its
-- own database across the assessment-service, content-service,
-- verification-service, career-service, realtime-service and analytics-
-- service extractions — see each service's own V1 migration for exactly
-- where its data lives now and why the cross-service foreign keys among
-- them were never reproduced there.
--
-- DROP TABLE ... CASCADE, same reasoning as V18: this only removes
-- dependent objects (foreign key constraints defined on other tables that
-- reference one of these), never the referencing tables or their rows.
-- Two tables outside this list are affected exactly that way and nothing
-- more:
--   - gamification's leaderboard_entries.course_id -> courses(id). That
--     module is a separately-tracked gap (no owning service since the
--     Sprint 6 split, per the build checklist) — its table and data are
--     untouched, only the now-cross-database constraint comes off.
--   - item_statistics.item_id -> items(id) — unimplemented anywhere (see
--     V18's own comment on this pattern), same treatment.
--
-- After this migration, every table below exists only in its owning
-- service's own database. What is left in `skillsphere` is: nothing from
-- the original Sprint 5 domain — nine service extractions have now moved
-- every table with a real owner out. Only gamification (orphaned) and the
-- two other dead V9 tables not already removed by V18 remain, all
-- unimplemented by any service.
-- =============================================================================

DROP TABLE IF EXISTS
    -- assessment-service (skill graph + assessment)
    skill_prerequisites,
    learner_skill_state,
    skill_mastery_history,
    item_options,
    items,
    assessment_items,
    responses,
    assessments,
    misconceptions,
    skills,
    skill_categories,
    -- content-service
    lesson_skills,
    lesson_progress,
    enrollments,
    lessons,
    course_modules,
    courses,
    course_categories,
    -- verification-service
    ai_usage_declarations,
    process_events,
    viva_turns,
    viva_sessions,
    instructor_reviews,
    evidence,
    project_skills,
    submissions,
    projects,
    -- career-service
    path_steps,
    learning_paths,
    learner_career_goals,
    passport_snapshots,
    role_skill_requirements,
    career_roles,
    -- realtime-service
    arena_answers,
    arena_questions,
    arena_participants,
    confusion_signals,
    arenas,
    -- analytics-service
    risk_scores,
    daily_activity,
    learning_events
CASCADE;
