package com.skillsphere.identity.domain;

/**
 * The four roles. Deliberately a closed set rather than free-form permissions:
 * a small fixed vocabulary is auditable, and permission systems that can express
 * anything tend to end up expressing something nobody intended.
 */
public enum RoleName {

    /** Learns, is assessed, defends work, owns a skill passport. */
    LEARNER,

    /** Authors content and items, reviews submissions, runs live sessions. */
    INSTRUCTOR,

    /** Governs the skill graph, career catalogue, accounts and settings. */
    ADMIN,

    /**
     * Reads skill passports shared with them and the evidence beneath.
     * Read-only by construction — this role can never write learning data.
     */
    EMPLOYER
}
