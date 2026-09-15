package com.skillsphere.skill;

import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.skill.domain.LearnerSkillState;
import com.skillsphere.skill.domain.LearnerSkillStateRepository;
import com.skillsphere.skill.domain.Skill;
import com.skillsphere.skill.domain.SkillRepository;
import com.skillsphere.skill.internal.SkillGraphService;
import com.skillsphere.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The skill graph's two load-bearing behaviours: it must stay acyclic, and it
 * must correctly answer what a learner is ready to study.
 *
 * <p>Cycle prevention gets the most coverage here because a cycle is a
 * platform-wide outage rather than a local bug. Path generation would never
 * terminate and the frontier query would return nothing for <em>every</em>
 * learner, because no skill's prerequisites could ever all be satisfied. One bad
 * edge from one admin, and nobody can learn anything.
 */
class SkillGraphTest extends IntegrationTest {

    @Autowired
    SkillGraphService graph;

    @Autowired
    SkillRepository skills;

    @Autowired
    LearnerSkillStateRepository learnerStates;

    private static final BigDecimal HARD = BigDecimal.ONE;
    private static final BigDecimal SOFT = new BigDecimal("0.50");
    private static final BigDecimal MASTERY_THRESHOLD = new BigDecimal("0.80");

    private Skill newSkill(String name) {
        Skill skill = new Skill(name.toLowerCase() + "-" + UUID.randomUUID(), name);
        return skills.save(skill);
    }

    /**
     * A synthetic but unique learner id.
     *
     * <p>Used to be a real row inserted into a {@code users} table this
     * database also owned, because {@code learner_skill_state} carried a
     * foreign key to it — see this service's own V1 migration for why that
     * FK is gone now that identity-service owns {@code users} in its own
     * database: {@code learner_skill_state.user_id} is a plain, unenforced
     * column, the same trust boundary as everywhere else in this split (the
     * caller is already an authenticated learner by the time this table is
     * written). Nothing here needs a real user row any more, only an id
     * that will not collide with another test's.
     */
    private long newLearnerId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
    }

    private void master(long userId, Skill skill, String mastery) {
        LearnerSkillState state = new LearnerSkillState(userId, skill);
        state.updateMastery(new BigDecimal(mastery));
        learnerStates.save(state);
    }

    // -----------------------------------------------------------------
    // Acyclicity
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a skill cannot be its own prerequisite")
    void selfPrerequisiteRejected() {
        Skill java = newSkill("Java");

        assertThatThrownBy(() -> graph.addPrerequisite(java.getId(), java.getId(), HARD))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be its own prerequisite");
    }

    @Test
    @DisplayName("a direct two-node cycle is rejected")
    void directCycleRejected() {
        Skill oop = newSkill("OOP");
        Skill collections = newSkill("Collections");

        // Collections requires OOP — fine.
        graph.addPrerequisite(collections.getId(), oop.getId(), HARD);

        // OOP requires Collections — now they require each other.
        assertThatThrownBy(() -> graph.addPrerequisite(oop.getId(), collections.getId(), HARD))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("loop");
    }

    /**
     * The case a naive implementation misses. Checking only direct edges catches
     * A→B→A but happily accepts A→B→C→A, and the resulting cycle is invisible
     * until path generation hangs.
     */
    @Test
    @DisplayName("an indirect cycle three levels deep is rejected")
    void transitiveCycleRejected() {
        Skill a = newSkill("Syntax");
        Skill b = newSkill("OOP");
        Skill c = newSkill("Generics");

        graph.addPrerequisite(b.getId(), a.getId(), HARD);  // OOP needs Syntax
        graph.addPrerequisite(c.getId(), b.getId(), HARD);  // Generics needs OOP

        // Syntax needs Generics would close the loop Syntax → Generics → OOP → Syntax.
        assertThatThrownBy(() -> graph.addPrerequisite(a.getId(), c.getId(), HARD))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("loop");
    }

    @Test
    @DisplayName("a diamond is allowed — it is not a cycle")
    void diamondIsAllowed() {
        Skill base = newSkill("Java Basics");
        Skill left = newSkill("Collections");
        Skill right = newSkill("Streams");
        Skill top = newSkill("Concurrency");

        graph.addPrerequisite(left.getId(), base.getId(), HARD);
        graph.addPrerequisite(right.getId(), base.getId(), HARD);
        graph.addPrerequisite(top.getId(), left.getId(), HARD);

        // Two paths converge on the same node. A cycle check that rejected any
        // repeated node would wrongly refuse this, and diamonds are extremely
        // common in a real curriculum.
        graph.addPrerequisite(top.getId(), right.getId(), HARD);

        assertThat(graph.findAllPrerequisites(top.getId()))
                .extracting(Skill::getId)
                .containsExactlyInAnyOrder(left.getId(), right.getId(), base.getId());
    }

    @Test
    @DisplayName("the same edge cannot be added twice")
    void duplicateEdgeRejected() {
        Skill a = newSkill("A");
        Skill b = newSkill("B");
        graph.addPrerequisite(a.getId(), b.getId(), HARD);

        assertThatThrownBy(() -> graph.addPrerequisite(a.getId(), b.getId(), HARD))
                .hasMessageContaining("already recorded");
    }

    // -----------------------------------------------------------------
    // Traversal
    // -----------------------------------------------------------------

    @Test
    @DisplayName("prerequisites are found transitively, not just directly")
    void transitivePrerequisites() {
        Skill syntax = newSkill("Syntax");
        Skill oop = newSkill("OOP");
        Skill generics = newSkill("Generics");
        Skill collections = newSkill("Collections");

        graph.addPrerequisite(oop.getId(), syntax.getId(), HARD);
        graph.addPrerequisite(generics.getId(), oop.getId(), HARD);
        graph.addPrerequisite(collections.getId(), generics.getId(), HARD);

        assertThat(graph.findAllPrerequisites(collections.getId()))
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder("Generics", "OOP", "Syntax");
    }

    @Test
    @DisplayName("mastering a skill reveals everything it unlocks")
    void descendantsAreFound() {
        Skill base = newSkill("Java Basics");
        Skill oop = newSkill("OOP");
        Skill spring = newSkill("Spring");

        graph.addPrerequisite(oop.getId(), base.getId(), HARD);
        graph.addPrerequisite(spring.getId(), oop.getId(), HARD);

        assertThat(graph.findUnlockedBy(base.getId()))
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder("OOP", "Spring");
    }

    // -----------------------------------------------------------------
    // The frontier
    // -----------------------------------------------------------------

    @Test
    @DisplayName("a skill with unmet hard prerequisites is not offered")
    void gatedSkillNotOffered() {
        long learner = newLearnerId();
        Skill base = newSkill("Java Basics");
        Skill advanced = newSkill("Concurrency");
        graph.addPrerequisite(advanced.getId(), base.getId(), HARD);

        List<Skill> ready = graph.findReadyToLearn(learner, MASTERY_THRESHOLD);

        assertThat(ready).extracting(Skill::getId)
                .as("a root skill is always available")
                .contains(base.getId())
                .as("a gated skill must stay closed until its gate is met")
                .doesNotContain(advanced.getId());
    }

    @Test
    @DisplayName("mastering the prerequisite opens the next skill")
    void masteringPrerequisiteOpensNext() {
        long learner = newLearnerId();
        Skill base = newSkill("Java Basics");
        Skill advanced = newSkill("Concurrency");
        graph.addPrerequisite(advanced.getId(), base.getId(), HARD);

        master(learner, base, "0.9000");

        assertThat(graph.findReadyToLearn(learner, MASTERY_THRESHOLD))
                .extracting(Skill::getId)
                .as("the gate is satisfied, so the next skill opens")
                .contains(advanced.getId())
                .as("an already-mastered skill should not be offered again")
                .doesNotContain(base.getId());
    }

    /**
     * Soft edges advise an ordering without blocking. If they gated, the graph
     * would collapse into a single rigid sequence — which is precisely the
     * sequential unlocking this product exists to replace.
     */
    @Test
    @DisplayName("a soft prerequisite advises but never blocks")
    void softPrerequisiteDoesNotBlock() {
        long learner = newLearnerId();
        Skill helpful = newSkill("Maths");
        Skill target = newSkill("Algorithms");
        graph.addPrerequisite(target.getId(), helpful.getId(), SOFT);

        assertThat(graph.findReadyToLearn(learner, MASTERY_THRESHOLD))
                .extracting(Skill::getId)
                .as("a soft edge must not gate progression")
                .contains(target.getId());
    }

    @Test
    @DisplayName("partial mastery of a gate is not enough")
    void partialMasteryDoesNotOpenGate() {
        long learner = newLearnerId();
        Skill base = newSkill("Java Basics");
        Skill advanced = newSkill("Concurrency");
        graph.addPrerequisite(advanced.getId(), base.getId(), HARD);

        // Below the 0.80 threshold.
        master(learner, base, "0.6000");

        assertThat(graph.findReadyToLearn(learner, MASTERY_THRESHOLD))
                .extracting(Skill::getId)
                .doesNotContain(advanced.getId());
    }

    @Test
    @DisplayName("every gate must be met, not just one of them")
    void allGatesRequired() {
        long learner = newLearnerId();
        Skill java = newSkill("Java");
        Skill sql = newSkill("SQL");
        Skill backend = newSkill("Backend Services");

        graph.addPrerequisite(backend.getId(), java.getId(), HARD);
        graph.addPrerequisite(backend.getId(), sql.getId(), HARD);

        master(learner, java, "0.9500");

        assertThat(graph.findReadyToLearn(learner, MASTERY_THRESHOLD))
                .extracting(Skill::getId)
                .as("one gate met out of two is not enough")
                .doesNotContain(backend.getId());

        master(learner, sql, "0.9000");

        assertThat(graph.findReadyToLearn(learner, MASTERY_THRESHOLD))
                .extracting(Skill::getId)
                .as("with both gates met the skill opens")
                .contains(backend.getId());
    }

    @Test
    @DisplayName("blocking prerequisites are named, so a lock can be explained")
    void blockingPrerequisitesAreIdentified() {
        long learner = newLearnerId();
        Skill java = newSkill("Java");
        Skill sql = newSkill("SQL");
        Skill backend = newSkill("Backend Services");

        graph.addPrerequisite(backend.getId(), java.getId(), HARD);
        graph.addPrerequisite(backend.getId(), sql.getId(), HARD);
        master(learner, java, "0.9500");

        // "Locked" is useless on its own; the learner needs to know what to do
        // about it.
        assertThat(graph.findBlockingPrerequisites(learner, backend.getId(), MASTERY_THRESHOLD))
                .extracting(Skill::getName)
                .containsExactly("SQL");
    }

    @Test
    @DisplayName("two learners with different histories get different frontiers")
    void frontiersDivergeByLearner() {
        long ahead = newLearnerId();
        long behind = newLearnerId();

        Skill base = newSkill("Java Basics");
        Skill next = newSkill("Collections");
        graph.addPrerequisite(next.getId(), base.getId(), HARD);

        master(ahead, base, "0.9000");

        // The claim the whole product rests on: the same graph produces a
        // different route for each person.
        assertThat(graph.findReadyToLearn(ahead, MASTERY_THRESHOLD))
                .extracting(Skill::getId).contains(next.getId());
        assertThat(graph.findReadyToLearn(behind, MASTERY_THRESHOLD))
                .extracting(Skill::getId).doesNotContain(next.getId());
    }
}
