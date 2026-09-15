package com.skillsphere.assessment.domain;

public enum AssessmentType {
    /** Establishes a starting profile with no prior evidence. Adaptive in length. */
    DIAGNOSTIC,
    /** Ongoing practice against a known gap. */
    PRACTICE,
    /** Confirms mastery before a path advances. */
    CHECKPOINT,
    /** Live competitive session — same items, but everyone sees the same question. */
    ARENA
}
