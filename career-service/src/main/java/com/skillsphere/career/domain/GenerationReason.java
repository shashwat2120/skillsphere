package com.skillsphere.career.domain;

/** Why a particular version of a learning path was produced. */
public enum GenerationReason {
    INITIAL,
    DIAGNOSTIC_COMPLETED,
    MASTERY_CHANGED,
    GOAL_CHANGED,
    DECAY_DETECTED,
    MANUAL
}
