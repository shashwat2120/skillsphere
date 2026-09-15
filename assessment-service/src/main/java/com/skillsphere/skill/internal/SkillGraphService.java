package com.skillsphere.skill.internal;

import com.skillsphere.shared.audit.AuditLogger;
import com.skillsphere.shared.error.ConflictException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.skill.SkillPrerequisiteChanged;
import com.skillsphere.skill.domain.LearnerSkillStateRepository;
import com.skillsphere.skill.domain.Skill;
import com.skillsphere.skill.domain.SkillPrerequisite;
import com.skillsphere.skill.domain.SkillPrerequisiteRepository;
import com.skillsphere.skill.domain.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Guards the shape of the skill graph.
 *
 * <p>Its single most important job is keeping the graph acyclic. A cycle is not
 * a cosmetic problem: path generation would never terminate, the frontier query
 * would return nothing because no skill's prerequisites could ever all be
 * satisfied, and every learner on the platform would be stuck simultaneously
 * with no obvious cause. One bad edge added by one admin would take the product
 * down.
 *
 * <p>Because PostgreSQL cannot express acyclicity as a constraint, this class is
 * the only thing standing between an admin's editor and that outcome — the rare
 * case where the database genuinely cannot be the last line of defence, so the
 * service layer has to be.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGraphService {

    /**
     * Ceiling on recursive traversal depth.
     *
     * <p>Generous — a real prerequisite chain beyond twenty is a modelling
     * mistake, not a legitimate curriculum — and present mainly so a traversal
     * can never run away if a malformed edge ever reaches the table.
     */
    private static final int MAX_DEPTH = 20;

    private final SkillRepository skills;
    private final SkillPrerequisiteRepository prerequisites;
    private final LearnerSkillStateRepository learnerStates;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------------
    // Edges
    // -----------------------------------------------------------------

    /**
     * Adds a prerequisite edge, refusing anything that would break the DAG.
     *
     * <p>Four checks, in increasing cost order so the cheap ones reject first and
     * the recursive query only runs when it has to.
     */
    @Transactional
    public SkillPrerequisite addPrerequisite(Long skillId, Long prerequisiteId, BigDecimal strength) {
        if (skillId.equals(prerequisiteId)) {
            // Also blocked by a CHECK constraint, but caught here so the caller
            // gets a comprehensible message rather than a constraint violation.
            throw new ValidationException("SELF_PREREQUISITE",
                    "A skill cannot be its own prerequisite.");
        }

        Skill skill = skills.findById(skillId)
                .orElseThrow(() -> new NotFoundException("Skill", skillId));
        Skill prerequisite = skills.findById(prerequisiteId)
                .orElseThrow(() -> new NotFoundException("Skill", prerequisiteId));

        if (prerequisites.existsBySkillIdAndPrerequisiteSkillId(skillId, prerequisiteId)) {
            throw new ConflictException("DUPLICATE_PREREQUISITE",
                    "That prerequisite is already recorded.");
        }

        // The expensive check, and the one that matters. The edge asserts that
        // the prerequisite comes before the skill, so it is a contradiction
        // exactly when the skill already comes before the prerequisite.
        if (prerequisites.wouldCreateCycle(skillId, prerequisiteId, MAX_DEPTH)) {
            log.warn("Rejected prerequisite edge {} -> {}: would create a cycle",
                    prerequisiteId, skillId);
            throw new ValidationException("CYCLIC_PREREQUISITE",
                    "'%s' already depends on '%s', so adding this would create a loop."
                            .formatted(prerequisite.getName(), skill.getName()));
        }

        SkillPrerequisite edge = new SkillPrerequisite(skill, prerequisite, strength);
        log.info("Added prerequisite: '{}' requires '{}' (strength {})",
                skill.getName(), prerequisite.getName(), strength);
        SkillPrerequisite saved = prerequisites.save(edge);
        auditLogger.record("SKILL_PREREQUISITE_ADDED", "SKILL", skillId, null, edgeJson(saved), null);
        events.publishEvent(new SkillPrerequisiteChanged(skillId, prerequisiteId, strength.doubleValue(), false));
        return saved;
    }

    @Transactional
    public void removePrerequisite(Long skillId, Long prerequisiteId) {
        SkillPrerequisite edge = prerequisites.findBySkillIdAndPrerequisiteSkillId(skillId, prerequisiteId)
                .orElseThrow(() -> new NotFoundException("Prerequisite edge not found."));
        prerequisites.deleteBySkillIdAndPrerequisiteSkillId(skillId, prerequisiteId);
        auditLogger.record("SKILL_PREREQUISITE_REMOVED", "SKILL", skillId, edgeJson(edge), null, null);
        events.publishEvent(new SkillPrerequisiteChanged(skillId, prerequisiteId, edge.getStrength().doubleValue(), true));
    }

    private String edgeJson(SkillPrerequisite edge) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("skillId", edge.getSkill().getId());
        node.put("skillName", edge.getSkill().getName());
        node.put("prerequisiteSkillId", edge.getPrerequisiteSkill().getId());
        node.put("prerequisiteSkillName", edge.getPrerequisiteSkill().getName());
        node.put("strength", edge.getStrength());
        return node.toString();
    }

    // -----------------------------------------------------------------
    // Traversal
    // -----------------------------------------------------------------

    /** Everything this skill transitively depends on. */
    @Transactional(readOnly = true)
    public List<Skill> findAllPrerequisites(Long skillId) {
        List<Long> ids = prerequisites.findAncestorIds(skillId, MAX_DEPTH);
        return ids.isEmpty() ? List.of() : skills.findAllById(ids);
    }

    /** Everything that becomes reachable once this skill is mastered. */
    @Transactional(readOnly = true)
    public List<Skill> findUnlockedBy(Long skillId) {
        List<Long> ids = prerequisites.findDescendantIds(skillId, MAX_DEPTH);
        return ids.isEmpty() ? List.of() : skills.findAllById(ids);
    }

    /**
     * What this learner is ready to start now.
     *
     * <p>One query, no N+1 — see {@link SkillRepository#findReadyToLearn}. This
     * sits behind every dashboard render, so its cost is felt on every page load
     * rather than occasionally.
     */
    @Transactional(readOnly = true)
    public List<Skill> findReadyToLearn(Long userId, BigDecimal masteryThreshold) {
        return skills.findReadyToLearn(userId, masteryThreshold.doubleValue());
    }

    /** Entry points for a learner with no history. */
    @Transactional(readOnly = true)
    public List<Skill> findRootSkills() {
        return skills.findRootSkills();
    }

    /**
     * Whether every hard prerequisite of a skill is mastered.
     *
     * <p>Used to explain a locked skill rather than merely report it: the caller
     * can list precisely which prerequisites are outstanding, which is what turns
     * "locked" into "finish Collections first".
     */
    @Transactional(readOnly = true)
    public boolean hasMetPrerequisites(Long userId, Long skillId, BigDecimal threshold) {
        List<Long> gates = prerequisites.findHardPrerequisiteIds(skillId);
        if (gates.isEmpty()) {
            return true;
        }
        return learnerStates.countMastered(userId, gates, threshold) == gates.size();
    }

    /** Hard prerequisites the learner has not yet mastered. */
    @Transactional(readOnly = true)
    public List<Skill> findBlockingPrerequisites(Long userId, Long skillId, BigDecimal threshold) {
        List<Long> gates = prerequisites.findHardPrerequisiteIds(skillId);
        if (gates.isEmpty()) {
            return List.of();
        }
        List<Long> mastered = learnerStates.findByUserIdAndSkillIds(userId, gates).stream()
                .filter(state -> state.getMasteryProbability().compareTo(threshold) >= 0)
                .map(state -> state.getSkill().getId())
                .toList();

        return skills.findAllById(gates.stream().filter(id -> !mastered.contains(id)).toList());
    }
}
