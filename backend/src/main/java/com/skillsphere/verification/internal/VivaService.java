package com.skillsphere.verification.internal;

import com.skillsphere.shared.error.ForbiddenException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.verification.domain.Evidence;
import com.skillsphere.verification.domain.EvidenceRepository;
import com.skillsphere.verification.domain.EvidenceSourceType;
import com.skillsphere.verification.domain.EvidenceType;
import com.skillsphere.verification.domain.Project;
import com.skillsphere.verification.domain.ProjectRepository;
import com.skillsphere.verification.domain.ProjectSkill;
import com.skillsphere.verification.domain.ProjectSkillRepository;
import com.skillsphere.verification.domain.Submission;
import com.skillsphere.verification.domain.SubmissionRepository;
import com.skillsphere.verification.domain.SubmissionStatus;
import com.skillsphere.verification.domain.VivaSession;
import com.skillsphere.verification.domain.VivaSessionRepository;
import com.skillsphere.verification.domain.VivaStatus;
import com.skillsphere.verification.domain.VivaTurn;
import com.skillsphere.verification.domain.VivaTurnRepository;
import com.skillsphere.verification.domain.VivaVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The scalable oral defence, turn by turn.
 *
 * <p>Questions are generated from the submission and cannot exist before it
 * is made — that ordering, not any AI-detection step, is what makes the
 * defence impossible to outsource. A verdict is reached in one of three
 * states rather than a pass/fail binary: {@code INCONCLUSIVE} exists because
 * a low-confidence evaluation is a fact about the evaluation, not licence to
 * force a guess — those sessions fall back to {@code AWAITING_REVIEW} for a
 * human instead of a machine deciding under real uncertainty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VivaService {

    private final SubmissionRepository submissions;
    private final ProjectRepository projects;
    private final ProjectSkillRepository projectSkills;
    private final VivaSessionRepository vivaSessions;
    private final VivaTurnRepository vivaTurns;
    private final EvidenceRepository evidence;
    private final VivaQuestionGenerator generator;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${skillsphere.viva.default-turns:5}")
    private int defaultTurns;

    @Value("${skillsphere.viva.pass-threshold:0.60}")
    private double passThreshold;

    @Value("${skillsphere.viva.turn-time-limit-seconds:180}")
    private int turnTimeLimitSeconds;

    /** Minimum average grader confidence before a verdict is trusted at all. */
    private static final double CONFIDENCE_FLOOR = 0.35;

    public record TurnView(Long id, int position, String question, String anchor, String intent,
                            int timeLimitSec, boolean answered) {
    }

    public record VivaResult(Long vivaSessionId, String status, String verdict, Double overallScore,
                              int turnsCompleted, int turnsPlanned, TurnView currentTurn) {
    }

    @Transactional
    public VivaResult start(Long submissionId, Long userId) {
        Submission submission = requireOwnedSubmission(submissionId, userId);
        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new ValidationException("NOT_SUBMITTED", "This submission has not been submitted yet.");
        }

        Project project = projects.findById(submission.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project", submission.getProjectId()));

        VivaSession session = new VivaSession(submissionId, defaultTurns, ollamaClient.model());
        session = vivaSessions.save(session);

        submission.setStatus(SubmissionStatus.IN_VIVA);
        submissions.save(submission);

        VivaQuestionGenerator.GeneratedQuestion q = generator.firstQuestion(
                submission.getContent(), project.getTitle(), nullToEmpty(project.getBrief()), project.getRubric());

        VivaTurn turn = new VivaTurn(session.getId(), 1, q.question(), q.anchor(), q.intent(), null,
                turnTimeLimitSeconds);
        turn = vivaTurns.save(turn);

        log.info("Started viva {} for submission {} (model {})", session.getId(), submissionId, session.getGeneratorModel());

        return toResult(session, turn);
    }

    @Transactional
    public VivaResult answer(Long vivaSessionId, Long userId, Long turnId, String answerText) {
        VivaSession session = vivaSessions.findById(vivaSessionId)
                .orElseThrow(() -> new NotFoundException("Viva session", vivaSessionId));
        Submission submission = requireOwnedSubmission(session.getSubmissionId(), userId);

        if (session.getStatus() != VivaStatus.IN_PROGRESS) {
            throw new ValidationException("VIVA_CLOSED", "This viva has already finished.");
        }

        VivaTurn turn = vivaTurns.findById(turnId)
                .orElseThrow(() -> new NotFoundException("Viva turn", turnId));
        if (!turn.getVivaSessionId().equals(vivaSessionId)) {
            throw new ValidationException("WRONG_SESSION", "That question does not belong to this viva.");
        }
        if (turn.isAnswered()) {
            throw new ValidationException("ALREADY_ANSWERED", "This question has already been answered.");
        }
        if (answerText == null || answerText.isBlank()) {
            throw new ValidationException("EMPTY_ANSWER", "An answer is required.");
        }

        VivaQuestionGenerator.Evaluation eval = generator.evaluateAnswer(
                turn.getQuestion(), answerText, submission.getContent());

        turn.recordAnswer(answerText, toJson(eval), BigDecimal.valueOf(eval.score()).setScale(2, RoundingMode.HALF_UP));
        vivaTurns.save(turn);
        session.recordTurnCompleted();

        if (session.getTurnsCompleted() < session.getTurnsPlanned()) {
            List<VivaTurn> priorTurns = vivaTurns.findByVivaSessionIdOrderByPosition(vivaSessionId);
            List<VivaQuestionGenerator.PriorTurn> history = priorTurns.stream()
                    .filter(VivaTurn::isAnswered)
                    .map(t -> new VivaQuestionGenerator.PriorTurn(
                            t.getQuestion(), t.getAnswer(), t.getScore().doubleValue()))
                    .toList();

            VivaQuestionGenerator.GeneratedQuestion q = generator.followUpQuestion(submission.getContent(), history);
            VivaTurn nextTurn = new VivaTurn(vivaSessionId, session.getTurnsCompleted() + 1,
                    q.question(), q.anchor(), q.intent(), null, turnTimeLimitSeconds);
            nextTurn = vivaTurns.save(nextTurn);
            vivaSessions.save(session);

            return toResult(session, nextTurn);
        }

        return conclude(session, submission);
    }

    private VivaResult conclude(VivaSession session, Submission submission) {
        List<VivaTurn> turns = vivaTurns.findByVivaSessionIdOrderByPosition(session.getId());
        double avgScore = turns.stream().mapToDouble(t -> t.getScore().doubleValue()).average().orElse(0.0);
        double avgConfidence = turns.stream()
                .mapToDouble(t -> readConfidence(t.getEvaluation()))
                .average().orElse(0.0);

        VivaVerdict verdict;
        if (avgConfidence < CONFIDENCE_FLOOR) {
            verdict = VivaVerdict.INCONCLUSIVE;
        } else if (avgScore >= passThreshold) {
            verdict = VivaVerdict.VERIFIED;
        } else {
            verdict = VivaVerdict.NOT_VERIFIED;
        }

        BigDecimal overallScore = BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP);
        session.conclude(verdict, overallScore);
        vivaSessions.save(session);

        submission.setStatus(switch (verdict) {
            case VERIFIED -> SubmissionStatus.VERIFIED;
            case NOT_VERIFIED -> SubmissionStatus.NOT_VERIFIED;
            case INCONCLUSIVE -> SubmissionStatus.AWAITING_REVIEW;
        });
        submissions.save(submission);

        if (verdict == VivaVerdict.VERIFIED) {
            issueEvidence(submission, session, overallScore);
        }

        log.info("Concluded viva {} for submission {} — verdict={} score={} confidence={}",
                session.getId(), submission.getId(), verdict, overallScore, round(avgConfidence));

        return toResult(session, null);
    }

    private void issueEvidence(Submission submission, VivaSession session, BigDecimal overallScore) {
        List<ProjectSkill> skills = projectSkills.findByProjectId(submission.getProjectId());
        for (ProjectSkill ps : skills) {
            Long skillId = ps.getId().getSkillId();
            if (evidence.existsBySourceTypeAndSourceIdAndSkillId(
                    EvidenceSourceType.VIVA_SESSION, session.getId(), skillId)) {
                continue;
            }
            evidence.save(new Evidence(submission.getUserId(), skillId, EvidenceType.VIVA,
                    EvidenceSourceType.VIVA_SESSION, session.getId(), ps.getWeight(), overallScore));
        }
    }

    private VivaResult toResult(VivaSession session, VivaTurn currentTurn) {
        TurnView turnView = currentTurn == null ? null : new TurnView(
                currentTurn.getId(), currentTurn.getPosition(), currentTurn.getQuestion(),
                currentTurn.getQuestionAnchor(), currentTurn.getQuestionIntent() == null
                        ? null : currentTurn.getQuestionIntent().name(),
                currentTurn.getTimeLimitSec(), currentTurn.isAnswered());

        return new VivaResult(session.getId(), session.getStatus().name(),
                session.getVerdict() == null ? null : session.getVerdict().name(),
                session.getOverallScore() == null ? null : session.getOverallScore().doubleValue(),
                session.getTurnsCompleted(), session.getTurnsPlanned(), turnView);
    }

    private Submission requireOwnedSubmission(Long submissionId, Long userId) {
        Submission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission", submissionId));
        if (!submission.getUserId().equals(userId)) {
            throw new ForbiddenException("That submission belongs to another learner.");
        }
        return submission;
    }

    private String toJson(VivaQuestionGenerator.Evaluation eval) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("score", eval.score());
        node.put("reasoning", eval.reasoning());
        node.put("confidence", eval.confidence());
        ArrayNode criteria = node.putArray("criteriaMet");
        eval.criteriaMet().forEach(criteria::add);
        ArrayNode flags = node.putArray("flags");
        eval.flags().forEach(flags::add);
        return node.toString();
    }

    private double readConfidence(String evaluationJson) {
        if (evaluationJson == null) return 0.0;
        return objectMapper.readTree(evaluationJson).path("confidence").asDouble(0.0);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
