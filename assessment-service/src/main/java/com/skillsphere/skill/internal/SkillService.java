package com.skillsphere.skill.internal;

import com.skillsphere.shared.audit.AuditLogger;
import com.skillsphere.shared.error.ConflictException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.skill.SkillCatalogChanged;
import com.skillsphere.skill.domain.LearnerSkillState;
import com.skillsphere.skill.domain.LearnerSkillStateRepository;
import com.skillsphere.skill.domain.Skill;
import com.skillsphere.skill.domain.SkillCategory;
import com.skillsphere.skill.domain.SkillCategoryRepository;
import com.skillsphere.skill.domain.SkillPrerequisiteRepository;
import com.skillsphere.skill.domain.SkillRepository;
import com.skillsphere.skill.web.SkillDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill catalogue management and the learner-facing view of the graph.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final int MAX_DEPTH = 20;

    private final SkillRepository skills;
    private final SkillCategoryRepository categories;
    private final SkillPrerequisiteRepository prerequisites;
    private final LearnerSkillStateRepository learnerStates;
    private final SkillGraphService graph;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------------
    // Catalogue
    // -----------------------------------------------------------------

    @Transactional
    public SkillDtos.SkillResponse create(SkillDtos.CreateSkillRequest request) {
        if (skills.existsBySlug(request.slug())) {
            throw new ConflictException("DUPLICATE_SLUG",
                    "A skill with that slug already exists.");
        }

        Skill skill = new Skill(request.slug(), request.name());
        skill.setDescription(request.description());
        applyOptional(skill, request.categoryId(), request.levelBand(),
                request.estMinutes(), request.decayRate());

        log.info("Created skill '{}' ({})", skill.getName(), skill.getSlug());
        Skill saved = skills.save(skill);
        auditLogger.record("SKILL_CREATED", "SKILL", saved.getId(), null, skillJson(saved), null);
        events.publishEvent(toCatalogChanged(saved));
        return toResponse(saved);
    }

    @Transactional
    public SkillDtos.SkillResponse update(Long id, SkillDtos.UpdateSkillRequest request) {
        Skill skill = skills.findById(id)
                .orElseThrow(() -> new NotFoundException("Skill", id));
        String before = skillJson(skill);

        // The slug is deliberately absent from the update request. It is a
        // stable public identifier: evidence rows, shared passport links and
        // external references all point at it, so renaming the skill must not
        // silently invalidate them.
        skill.setName(request.name());
        skill.setDescription(request.description());
        applyOptional(skill, request.categoryId(), request.levelBand(),
                request.estMinutes(), request.decayRate());

        if (request.active() != null) {
            skill.setActive(request.active());
        }
        auditLogger.record("SKILL_UPDATED", "SKILL", id, before, skillJson(skill), null);
        events.publishEvent(toCatalogChanged(skill));
        return toResponse(skill);
    }

    /**
     * Retires a skill without deleting it.
     *
     * <p>Hard deletion is not offered at all. Evidence rows, mastery history and
     * completed path steps reference this skill, and a learner's passport has to
     * keep meaning something after the catalogue moves on. Deleting would either
     * cascade away somebody's proof of competence or leave the passport pointing
     * at nothing.
     */
    @Transactional
    public void retire(Long id) {
        Skill skill = skills.findById(id)
                .orElseThrow(() -> new NotFoundException("Skill", id));
        String before = skillJson(skill);
        skill.setActive(false);
        log.info("Retired skill '{}'", skill.getName());
        auditLogger.record("SKILL_RETIRED", "SKILL", id, before, skillJson(skill), null);
        events.publishEvent(toCatalogChanged(skill));
    }

    @Transactional(readOnly = true)
    public List<SkillDtos.SkillResponse> listActive() {
        return skills.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SkillDtos.SkillGraphNode getGraphNode(Long id) {
        Skill skill = skills.findById(id)
                .orElseThrow(() -> new NotFoundException("Skill", id));

        List<SkillDtos.SkillRef> directPrereqs = prerequisites.findBySkillId(id).stream()
                .map(edge -> new SkillDtos.SkillRef(
                        edge.getPrerequisiteSkill().getId(),
                        edge.getPrerequisiteSkill().getName()))
                .toList();

        List<SkillDtos.SkillRef> directUnlocks = prerequisites.findByPrerequisiteSkillId(id).stream()
                .map(edge -> new SkillDtos.SkillRef(
                        edge.getSkill().getId(),
                        edge.getSkill().getName()))
                .toList();

        return new SkillDtos.SkillGraphNode(toResponse(skill), directPrereqs, directUnlocks);
    }

    // -----------------------------------------------------------------
    // Learner view
    // -----------------------------------------------------------------

    /**
     * The catalogue as one learner sees it: mastery, availability, and what is
     * blocking anything that is closed.
     *
     * <p><b>Built to avoid N+1 deliberately.</b> The naive shape loads skills,
     * then per skill loads mastery, then per skill loads prerequisites — three
     * queries per row on a page that renders the whole catalogue. Here mastery is
     * fetched once into a map, availability comes from the single frontier query,
     * and only genuinely blocked skills pay for a prerequisite lookup. Most
     * skills are either available or already mastered, so that last cost falls on
     * a small minority of rows.
     */
    @Transactional(readOnly = true)
    public List<SkillDtos.LearnerSkillResponse> listForLearner(Long userId, BigDecimal masteryThreshold) {
        List<Skill> all = skills.findByActiveTrueOrderByNameAsc();

        Map<Long, BigDecimal> mastery = new HashMap<>();
        for (LearnerSkillState state : learnerStates.findByUserId(userId)) {
            mastery.put(state.getSkill().getId(), state.getMasteryProbability());
        }

        List<Long> availableIds = graph.findReadyToLearn(userId, masteryThreshold)
                .stream().map(Skill::getId).toList();

        return all.stream().map(skill -> {
            BigDecimal m = mastery.getOrDefault(skill.getId(), BigDecimal.ZERO);
            boolean mastered = m.compareTo(masteryThreshold) >= 0;
            boolean available = availableIds.contains(skill.getId());

            List<SkillDtos.SkillRef> blockedBy = (mastered || available)
                    ? List.of()
                    : graph.findBlockingPrerequisites(userId, skill.getId(), masteryThreshold).stream()
                        .map(blocker -> new SkillDtos.SkillRef(blocker.getId(), blocker.getName()))
                        .toList();

            return new SkillDtos.LearnerSkillResponse(
                    skill.getId(), skill.getSlug(), skill.getName(), skill.getLevelBand(),
                    skill.getEstMinutes(), m, mastered, available, blockedBy);
        }).toList();
    }

    // -----------------------------------------------------------------

    /**
     * Nodes and edges in one response.
     *
     * <p>Returned together on purpose: fetching them separately makes the client
     * paint nodes with no connections for a frame, so the graph visibly
     * assembles itself and reads as a loading fault rather than a transition.
     */
    @Transactional(readOnly = true)
    public SkillDtos.LearnerGraphResponse graphForLearner(Long userId, BigDecimal masteryThreshold) {
        List<SkillDtos.LearnerSkillResponse> nodes = listForLearner(userId, masteryThreshold);

        List<SkillDtos.EdgeRef> edges = prerequisites.findAll().stream()
                .map(edge -> new SkillDtos.EdgeRef(
                        edge.getPrerequisiteSkill().getId(),
                        edge.getSkill().getId(),
                        edge.isHardGate()))
                .toList();

        return new SkillDtos.LearnerGraphResponse(nodes, edges);
    }

    private void applyOptional(Skill skill, Long categoryId, com.skillsphere.shared.domain.LevelBand band,
                               Integer estMinutes, BigDecimal decayRate) {
        if (categoryId != null) {
            SkillCategory category = categories.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Skill category", categoryId));
            skill.setCategory(category);
        }
        if (band != null) {
            skill.setLevelBand(band);
        }
        if (estMinutes != null) {
            skill.setEstMinutes(estMinutes);
        }
        if (decayRate != null) {
            skill.setDecayRate(decayRate);
        }
    }

    private SkillCatalogChanged toCatalogChanged(Skill skill) {
        return new SkillCatalogChanged(skill.getId(), skill.getSlug(), skill.getName(),
                skill.getLevelBand() == null ? null : skill.getLevelBand().name(), skill.isActive());
    }

    private String skillJson(Skill skill) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", skill.getName());
        node.put("description", skill.getDescription());
        node.put("levelBand", skill.getLevelBand() == null ? null : skill.getLevelBand().name());
        node.put("estMinutes", skill.getEstMinutes());
        node.put("active", skill.isActive());
        return node.toString();
    }

    private SkillDtos.SkillResponse toResponse(Skill skill) {
        return new SkillDtos.SkillResponse(
                skill.getId(), skill.getSlug(), skill.getName(), skill.getDescription(),
                skill.getCategory() == null ? null : skill.getCategory().getId(),
                skill.getCategory() == null ? null : skill.getCategory().getName(),
                skill.getLevelBand(), skill.getEstMinutes(), skill.getDecayRate(), skill.isActive());
    }
}
