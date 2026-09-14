package com.skillsphere.assessment.domain;

/**
 * Why an adaptive assessment stopped.
 *
 * <p>Recorded rather than inferred, because the reason changes what the result
 * means. An assessment that stopped on confidence produced a trustworthy
 * estimate; one that ran out of items produced whatever it managed to gather.
 * Presenting both as simply "completed" would let a weak measurement be read as
 * a strong one.
 */
public enum TerminationReason {
    /** The standard error fell below target — the good ending. */
    CONFIDENCE_REACHED,
    /** Hit the item cap first. The estimate is usable but less precise. */
    ITEM_CAP,
    TIME_LIMIT,
    ABANDONED,
    /** The bank was exhausted. A content problem, not a learner one. */
    NO_ITEMS_AVAILABLE
}
