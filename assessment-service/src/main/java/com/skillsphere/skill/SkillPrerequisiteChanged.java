package com.skillsphere.skill;

/**
 * Published whenever a prerequisite edge is added or removed.
 *
 * <p>Only career-service mirrors this today — {@link SkillLookup
 * #hardPrerequisitesOf} is the only method across every dependent service
 * that reads {@code skill_prerequisites} rather than {@code skills} itself,
 * and only {@code PathGenerator} calls it, to topologically order a
 * generated learning path. Same reasoning as {@link SkillCatalogChanged}:
 * edges change about as rarely as the skills they connect.
 */
public record SkillPrerequisiteChanged(
        Long skillId,
        Long prerequisiteSkillId,
        double strength,
        boolean removed) {
}
