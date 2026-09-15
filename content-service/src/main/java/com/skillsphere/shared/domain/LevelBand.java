package com.skillsphere.shared.domain;

/**
 * Roughly how advanced a skill is.
 *
  * <p>Lives in the shared kernel rather than in one module because three own it:
 * skills, courses and projects all describe their level this way. Duplicating the
 * enum per module would let the vocabularies drift, and a course labelled
 * ADVANCED would stop meaning the same thing as a skill labelled ADVANCED.
 *
 * <p>Note what this is <em>not</em>: it is not difficulty, and nothing in the
 * adaptive engine reads it. Difficulty is measured from real responses and lives
 * on the item as an IRT parameter, because an author's guess about how hard
 * something is has repeatedly been shown to be unreliable.
 *
 * <p>This exists for humans — grouping a catalogue, labelling a career roadmap,
 * ordering an admin's view of the graph. Keeping the two ideas apart matters: if
 * the engine ever routed on a declared band, the platform would be back to
 * teaching by opinion rather than by evidence.
 */
public enum LevelBand {
    FOUNDATIONAL,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}
