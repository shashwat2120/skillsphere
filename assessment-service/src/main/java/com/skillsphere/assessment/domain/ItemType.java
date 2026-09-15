package com.skillsphere.assessment.domain;

/**
 * How an item is answered.
 *
 * <p>MCQ dominates on purpose. It is the only type where a wrong answer carries
 * diagnostic information for free: the distractor chosen identifies the specific
 * misconception. A free-text answer needs marking before it says anything, which
 * is exactly the cost the adaptive loop cannot afford on every question.
 */
public enum ItemType {
    MCQ,
    MULTI_SELECT,
    NUMERIC,
    SHORT_TEXT,
    CODE
}
