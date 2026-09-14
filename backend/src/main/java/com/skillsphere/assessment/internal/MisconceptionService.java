package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.domain.Misconception;
import com.skillsphere.assessment.domain.MisconceptionRepository;
import com.skillsphere.assessment.web.ItemDtos;
import com.skillsphere.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The misconception library.
 *
 * <p>Reusable across items on purpose. The same false belief shows up behind
 * several different questions, and recording it once means the platform can count
 * how often it appears across a whole cohort — which converts a private learner
 * error into a public teaching signal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MisconceptionService {

    private final MisconceptionRepository misconceptions;

    @Transactional
    public ItemDtos.MisconceptionResponse create(ItemDtos.CreateMisconceptionRequest request) {
        Misconception misconception = new Misconception(
                request.skillId(), request.name(), request.remediationHint());
        misconception.setDescription(request.description());
        misconception.setRemediationLessonId(request.remediationLessonId());
        return toResponse(misconceptions.save(misconception));
    }

    @Transactional(readOnly = true)
    public List<ItemDtos.MisconceptionResponse> listBySkill(Long skillId) {
        return misconceptions.findBySkillId(skillId).stream().map(this::toResponse).toList();
    }

    /**
     * The misconceptions a cohort holds most often.
     *
     * <p>Read as a signal about teaching rather than about learners. A belief
     * appearing across many people is being produced by the explanation, and no
     * correct/incorrect tally could ever reveal that.
     */
    @Transactional(readOnly = true)
    public List<ItemDtos.MisconceptionResponse> mostObserved() {
        return misconceptions.findMostObserved().stream().map(this::toResponse).toList();
    }

    /** Called when a learner selects the distractor carrying this belief. */
    @Transactional
    public void recordObservation(Long misconceptionId) {
        misconceptions.findById(misconceptionId)
                .orElseThrow(() -> new NotFoundException("Misconception", misconceptionId))
                .observe();
    }

    private ItemDtos.MisconceptionResponse toResponse(Misconception m) {
        return new ItemDtos.MisconceptionResponse(
                m.getId(), m.getSkillId(), m.getName(), m.getDescription(),
                m.getRemediationHint(), m.getTimesObserved());
    }
}
