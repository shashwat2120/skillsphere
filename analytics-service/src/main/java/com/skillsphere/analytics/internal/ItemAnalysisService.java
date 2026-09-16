package com.skillsphere.analytics.internal;

import com.skillsphere.analytics.domain.LearningEvent;
import com.skillsphere.analytics.domain.LearningEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classical item analysis — p-value and discrimination — computed on demand
 * from {@link LearningEvent} rather than a scheduled job, unlike risk
 * scoring. An instructor asking "which items are worth revising" wants the
 * current answer for the filter they just picked (a skill, most likely),
 * not a snapshot that might be stale; the query is cheap enough at this
 * project's data volumes that caching it would only add complexity.
 *
 * <p><b>p-value</b> is the classroom-testing sense — proportion of
 * respondents who answered correctly — not a statistical-significance
 * p-value. The name is inherited from the field this technique comes from,
 * not renamed here to avoid confusing anyone who already knows item
 * analysis by this term.
 *
 * <p><b>Discrimination</b> uses the standard upper-lower 27% method: rank
 * every respondent to an item by their <em>overall</em> proportion-correct
 * across every {@code ITEM_ANSWERED} event they have ever produced — a
 * general-ability proxy, since this table has no IRT theta and does not
 * need one for a heuristic this coarse — then take the top and bottom 27%
 * of that ranking and subtract the bottom group's accuracy on this specific
 * item from the top group's. A well-discriminating item is one strong
 * learners get right noticeably more often than weak learners; a negative
 * or near-zero value flags an item that is either mis-keyed or not actually
 * testing the skill it claims to.
 */
@Service
@RequiredArgsConstructor
public class ItemAnalysisService {

    /**
     * The classical 27% cutoff (Kelley, 1939) — the point past which adding
     * more of the middle of the ability distribution stops sharpening the
     * contrast between "gets it" and "doesn't", the standard justification
     * for this exact fraction.
     */
    private static final double EDGE_FRACTION = 0.27;

    /**
     * Below this many respondents, discrimination is still computed —
     * never fabricated — but is unreliable enough that callers should
     * render it with a caveat rather than as a settled verdict on the item.
     */
    private static final int LOW_SAMPLE_THRESHOLD = 10;

    private final LearningEventRepository learningEvents;

    public record ItemStats(Long itemId, Long skillId, int respondentCount, double pValue,
                             double discrimination, boolean lowSample) {
    }

    /**
     * @param skillId when given, restricts the returned items to that skill;
     *                the ability ranking behind discrimination still draws
     *                on every respondent's <em>whole</em> history regardless
     *                of this filter, since narrowing the ability signal to
     *                one skill would just be a smaller, noisier version of
     *                the recent-accuracy factor risk scoring already covers.
     */
    @Transactional(readOnly = true)
    public List<ItemStats> analyze(Long skillId) {
        List<LearningEvent> allAnswers = learningEvents.findAllItemAnswered();
        if (allAnswers.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> abilityByUser = allAnswers.stream()
                .collect(Collectors.groupingBy(LearningEvent::getUserId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> proportionCorrect(e.getValue())));

        Map<Long, List<LearningEvent>> byItem = allAnswers.stream()
                .filter(e -> skillId == null || skillId.equals(e.getSkillId()))
                .collect(Collectors.groupingBy(LearningEvent::getEntityId));

        List<ItemStats> results = new ArrayList<>();
        byItem.forEach((itemId, responses) -> results.add(analyzeOne(itemId, responses, abilityByUser)));

        // Lowest discrimination first — the items most in need of an
        // instructor's attention surface without any extra sorting on their end.
        results.sort(Comparator.comparingDouble(ItemStats::discrimination));
        return results;
    }

    private ItemStats analyzeOne(Long itemId, List<LearningEvent> responses, Map<Long, Double> abilityByUser) {
        Long skillId = responses.get(0).getSkillId();
        double pValue = proportionCorrect(responses);

        List<Long> respondents = responses.stream()
                .map(LearningEvent::getUserId)
                .distinct()
                .sorted(Comparator.comparingDouble((Long u) -> abilityByUser.getOrDefault(u, 0.0)).reversed())
                .toList();

        int n = respondents.size();
        int groupSize = Math.max(1, (int) Math.round(n * EDGE_FRACTION));
        if (n >= 2) {
            // Never let the two edge groups overlap when there are enough
            // respondents to keep them separate — a degenerate n=1 case is
            // still handled (both groups collapse to the same respondent,
            // yielding discrimination 0, an honest answer for one data point).
            groupSize = Math.max(1, Math.min(groupSize, n / 2));
        }

        List<Long> topGroup = respondents.subList(0, groupSize);
        List<Long> bottomGroup = respondents.subList(n - groupSize, n);

        double topP = proportionCorrectForUsers(responses, topGroup);
        double bottomP = proportionCorrectForUsers(responses, bottomGroup);
        double discrimination = topP - bottomP;

        return new ItemStats(itemId, skillId, n, round(pValue), round(discrimination), n < LOW_SAMPLE_THRESHOLD);
    }

    private double proportionCorrect(List<LearningEvent> events) {
        if (events.isEmpty()) {
            return 0.0;
        }
        long correct = events.stream().filter(e -> Boolean.TRUE.equals(e.getCorrect())).count();
        return (double) correct / events.size();
    }

    private double proportionCorrectForUsers(List<LearningEvent> responses, List<Long> userIds) {
        Set<Long> subset = Set.copyOf(userIds);
        List<LearningEvent> filtered = responses.stream().filter(e -> subset.contains(e.getUserId())).toList();
        return proportionCorrect(filtered);
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
