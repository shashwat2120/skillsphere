package com.skillsphere.skill;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The skill module's public API for other modules.
 *
 * <p>Lives in the module root package, which makes it the only skill type others
 * may depend on. Everything under {@code skill.domain} and {@code skill.internal}
 * stays private, enforced by the boundary test.
 *
 * <p><b>Why this exists rather than sharing the repository.</b> Content, career
 * and assessment all legitimately need to resolve a skill id to a name, or check
 * that one exists. Handing them {@code SkillRepository} would give them the
 * ability to write skills, traverse the graph and load JPA entities — far more
 * than any of them needs, and every one of those capabilities becomes a coupling
 * that has to be untangled when the skill engine becomes its own service.
 *
 * <p>It also returns a record rather than the {@code Skill} entity. A JPA entity
 * crossing a module boundary drags a persistence context with it — lazy loading
 * fails outside a transaction, and callers end up managing sessions they should
 * know nothing about. This record is additionally the shape that survives Sprint
 * 6 unchanged: it is already what an HTTP response from the skill service would
 * carry.
 */
public interface SkillLookup {

    /** A skill, flattened to the fields other modules actually use. */
    record SkillInfo(Long id, String slug, String name, String levelBand, boolean active) {
    }

    Optional<SkillInfo> findById(Long skillId);

    boolean exists(Long skillId);

    /**
     * Resolves many ids at once.
     *
     * <p>Present so callers are not forced into an N+1: rendering a course with
     * forty tagged lessons should cost one lookup, not forty.
     */
    Map<Long, String> namesOf(Collection<Long> skillIds);

    List<SkillInfo> findAllActive();
}
