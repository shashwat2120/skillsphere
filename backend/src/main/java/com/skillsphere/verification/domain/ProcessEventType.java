package com.skillsphere.verification.domain;

public enum ProcessEventType {
    SESSION_START,
    SESSION_END,
    DRAFT_SAVED,
    EDIT,
    PASTE,
    LARGE_PASTE,
    RUN,
    TEST_PASS,
    TEST_FAIL,
    IDLE,
    AI_CONSULTED
}
