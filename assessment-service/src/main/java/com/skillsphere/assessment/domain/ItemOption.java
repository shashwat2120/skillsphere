package com.skillsphere.assessment.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One answer option — and, when wrong, the belief that leads a learner to it.
 *
 * <p>{@link #misconceptionId} is the field that makes this system diagnostic
 * rather than merely gradeable. In an ordinary quiz the three wrong options are
 * filler, chosen to be plausible and otherwise meaningless. Here each one is a
 * hypothesis about how a learner could be thinking, so the option they select
 * carries information even though the answer is wrong.
 *
 * <p>The practical consequence: "wrong, review Collections" becomes "you are
 * treating HashMap as ordered — here is why it is not". The first sends someone
 * back over material they mostly understood; the second addresses the one idea
 * that is actually broken.
 *
 * <p>An untagged distractor is not a bug, but it is a wasted opportunity: the
 * learner is told they are wrong and the platform learns nothing about why.
 * Authoring surfaces it as a warning for exactly that reason.
 */
@Entity
@Table(name = "item_options")
@Getter
@Setter
@NoArgsConstructor
public class ItemOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    /**
     * The false belief that makes this option attractive.
     *
     * <p>Null on the correct option, enforced by a CHECK constraint: a correct
     * answer cannot encode a misconception, and allowing it would let the engine
     * "diagnose" a learner who answered correctly.
     */
    @Column(name = "misconception_id")
    private Long misconceptionId;

    @Column(nullable = false)
    private int position = 0;

    /**
     * How often this option was chosen.
     *
     * <p>Distractor analysis reads this. A wrong option nobody ever picks is dead
     * weight that makes the item easier than intended; one picked more often than
     * the correct answer usually means the item is misleading rather than that
     * the cohort is weak.
     */
    @Column(name = "times_chosen", nullable = false)
    private int timesChosen = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ItemOption(Item item, String text, boolean correct, int position) {
        this.item = item;
        this.text = text;
        this.correct = correct;
        this.position = position;
    }

    public boolean isDiagnostic() {
        return !correct && misconceptionId != null;
    }
}
