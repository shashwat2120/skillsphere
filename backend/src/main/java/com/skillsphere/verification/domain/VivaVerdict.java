package com.skillsphere.verification.domain;

/**
 * The outcome of a viva. Deliberately not binary: a session can end
 * inconclusive rather than being forced into pass or fail when the evidence
 * genuinely does not support either — a low-confidence evaluation is a fact
 * about the evaluation, not license to guess.
 */
public enum VivaVerdict {
    VERIFIED,
    NOT_VERIFIED,
    INCONCLUSIVE
}
