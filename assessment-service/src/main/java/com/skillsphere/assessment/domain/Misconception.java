package com.skillsphere.assessment.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A specific, named false belief.
 *
 * <p>This is the entity that turns "wrong" into "here is what you misunderstand",
 * and it is one of the genuinely uncommon things in this system.
 *
 * <p>Almost every quiz engine records that an answer was incorrect and stops
 * there. The remediation it can then offer is "review this topic" — which sends
 * a learner back over material they have already read, most of which they
 * already understood. It is the least efficient possible response, and it is
 * what happens when the only thing recorded is a boolean.
 *
 * <p>Naming the belief instead — "thinks HashMap preserves insertion order" —
 * makes targeted remediation possible: address that one idea rather than
 * reteaching Collections. It also makes the platform's diagnosis auditable,
 * because the system has to commit to an actual claim about what the learner
 * thinks, which can then be right or wrong.
 *
 * <p>{@code timesObserved} matters at the cohort level. A misconception appearing
 * across many learners is not a learner problem, it is a teaching problem — the
 * instructor's explanation is producing it — and that is a signal no
 * correct/incorrect tally can surface.
 */
@Entity
@Table(name = "misconceptions")
@Getter
@Setter
@NoArgsConstructor
public class Misconception extends AuditableEntity {

    /** The skill this false belief sits inside. */
    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    /** Stated as a belief, not as a topic. "Thinks X" rather than "X". */
    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * What to say to someone holding this belief.
     *
     * <p>Mandatory. A diagnosis with no remedy is diagnosis theatre: it tells the
     * learner they are wrong in a more specific way and still leaves them with
     * nothing to do about it.
     */
    @Column(name = "remediation_hint", nullable = false, columnDefinition = "text")
    private String remediationHint;

    @Column(name = "remediation_lesson_id")
    private Long remediationLessonId;

    @Column(name = "times_observed", nullable = false)
    private int timesObserved = 0;

    public Misconception(Long skillId, String name, String remediationHint) {
        this.skillId = skillId;
        this.name = name;
        this.remediationHint = remediationHint;
    }

    public void observe() {
        timesObserved++;
    }
}
