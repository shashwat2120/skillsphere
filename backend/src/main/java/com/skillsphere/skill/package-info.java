/**
 * Skill engine: the skill graph, mastery estimation and the learner model.
 *
 * <p>The heart of the product, and the module with the strictest rule: it does
 * not depend on identity. A learner is referenced by user id as a plain value,
 * never as a mapped User entity. A JPA association here would quietly become a
 * join across a future service boundary, and those are precisely the couplings
 * that turn a microservices split into a rewrite.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Skill Engine",
        allowedDependencies = {"shared"})
package com.skillsphere.skill;
