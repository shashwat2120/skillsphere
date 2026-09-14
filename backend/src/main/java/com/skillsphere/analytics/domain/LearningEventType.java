package com.skillsphere.analytics.domain;

/**
 * What kind of learning event this is. Deliberately small for now — one
 * value, {@code ITEM_ANSWERED}, fed by the one event source this phase
 * actually wires up ({@code assessment.ResponseRecorded}). The column
 * behind it is a plain {@code VARCHAR(50)} with no CHECK constraint, so
 * growing this enum as more modules start publishing events never needs a
 * migration — new event sources are additive by design.
 */
public enum LearningEventType {
    ITEM_ANSWERED
}
