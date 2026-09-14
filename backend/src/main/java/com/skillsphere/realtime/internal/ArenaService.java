package com.skillsphere.realtime.internal;

import com.skillsphere.assessment.ArenaItemSource;
import com.skillsphere.realtime.domain.Arena;
import com.skillsphere.realtime.domain.ArenaAnswer;
import com.skillsphere.realtime.domain.ArenaAnswerRepository;
import com.skillsphere.realtime.domain.ArenaParticipant;
import com.skillsphere.realtime.domain.ArenaParticipantRepository;
import com.skillsphere.realtime.domain.ArenaQuestion;
import com.skillsphere.realtime.domain.ArenaQuestionRepository;
import com.skillsphere.realtime.domain.ArenaRepository;
import com.skillsphere.realtime.domain.ArenaStatus;
import com.skillsphere.shared.error.ForbiddenException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.skill.SkillLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * The live arena, start to finish.
 *
 * <p><b>Leaderboard reads from Postgres, not Redis.</b> The schema's own
 * comment names a Redis sorted set as the eventual answer for "would be the
 * bottleneck under per-answer live load" — a production concern at a scale
 * this project's demo classrooms don't reach. {@code idx_ap_arena_score}
 * already makes {@code findByArenaIdOrderByScoreDesc} an index scan; adding
 * a second, eventually-consistent store to keep in sync with the durable
 * record would be complexity spent on a problem this deployment doesn't
 * have. The interface this service exposes doesn't change if that ever
 * stops being true.
 *
 * <p><b>Advancing between questions is instructor-driven, not a server
 * timer.</b> A human running a live session already paces it — waiting for
 * stragglers, reacting to the room — and a timed auto-advance would take
 * that judgement away for a feature (server-scheduled STOMP ticks) that adds
 * real complexity for a live demo's marginal benefit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArenaService {

    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BASE_POINTS = 1000;
    private static final int MAX_SPEED_BONUS = 500;

    private final ArenaRepository arenas;
    private final ArenaParticipantRepository participants;
    private final ArenaQuestionRepository questions;
    private final ArenaAnswerRepository answers;
    private final ArenaItemSource itemSource;
    private final SkillLookup skillLookup;
    private final ConfusionDetectionService confusionDetection;
    private final SimpMessagingTemplate broker;
    private final Random random = new SecureRandom();

    // ---- views -------------------------------------------------------------

    public record ParticipantView(Long id, String displayName, int score, int correctCount,
                                   int answerCount, Integer finalRank) {
    }

    public record QuestionView(Long arenaQuestionId, int position, int totalQuestions,
                                String stem, List<ArenaItemSource.ArenaOption> options, int timeLimitSec) {
    }

    public record ArenaView(Long id, String title, String skillName, String joinCode, String status,
                             boolean allowGuests, int questionCount, int secondsPerQuestion,
                             int participantCount, QuestionView currentQuestion) {
    }

    public record AnswerResult(boolean correct, int pointsAwarded, int newScore, Long correctOptionId) {
    }

    // ---- lifecycle -----------------------------------------------------------

    @Transactional
    public ArenaView create(Long instructorId, String title, Long skillId, int questionCount, int secondsPerQuestion) {
        List<ArenaItemSource.ArenaItem> selected = itemSource.selectItemsForSkill(skillId, questionCount);
        if (selected.isEmpty()) {
            throw new ValidationException("NO_ITEMS", "This skill has no active items to build an arena from.");
        }

        Arena arena = arenas.save(
                new Arena(instructorId, skillId, title, generateJoinCode(), selected.size(), secondsPerQuestion));
        Long arenaId = arena.getId();

        List<ArenaQuestion> arenaQuestions = IntStream.range(0, selected.size())
                .mapToObj(i -> new ArenaQuestion(arenaId, selected.get(i).id(), i + 1, secondsPerQuestion))
                .toList();
        questions.saveAll(arenaQuestions);

        log.info("Arena {} created by instructor {} — {} questions, code {}",
                arena.getId(), instructorId, selected.size(), arena.getJoinCode());

        return toView(arena, null);
    }

    @Transactional(readOnly = true)
    public ArenaView findByJoinCode(String joinCode) {
        Arena arena = requireArenaByCode(joinCode);
        return toView(arena, currentQuestionView(arena));
    }

    /** For the instructor's own control panel — owner-only, unlike the public join-code preview. */
    @Transactional(readOnly = true)
    public ArenaView findForInstructor(Long instructorId, Long arenaId) {
        Arena arena = requireOwnedArena(arenaId, instructorId);
        return toView(arena, currentQuestionView(arena));
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> leaderboard(Long arenaId) {
        return participants.findByArenaIdOrderByScoreDesc(arenaId).stream().map(this::toParticipantView).toList();
    }

    @Transactional
    public ParticipantView join(String joinCode, Long userId, String guestToken, String requestedName) {
        Arena arena = requireArenaByCode(joinCode);
        if (!arena.isJoinable()) {
            throw new ValidationException("ARENA_NOT_JOINABLE", "This arena has already started or ended.");
        }
        if (userId == null && !arena.isAllowGuests()) {
            throw new ValidationException("GUESTS_NOT_ALLOWED", "This arena requires an account to join.");
        }

        Optional<ArenaParticipant> existing = userId != null
                ? participants.findByArenaIdAndUserId(arena.getId(), userId)
                : participants.findByArenaIdAndGuestToken(arena.getId(), guestToken);

        ArenaParticipant participant = existing.orElseGet(() -> {
            String name = displayNameFor(userId, requestedName);
            ArenaParticipant created = new ArenaParticipant(arena.getId(), userId,
                    userId == null ? guestToken : null, name);
            ArenaParticipant saved = participants.save(created);
            arena.setParticipantCount(arena.getParticipantCount() + 1);
            arenas.save(arena);
            broadcastLobby(arena);
            return saved;
        });

        return toParticipantView(participant);
    }

    @Transactional
    public ArenaView start(Long instructorId, Long arenaId) {
        Arena arena = requireOwnedArena(arenaId, instructorId);
        if (arena.getStatus() != ArenaStatus.LOBBY) {
            throw new ValidationException("ALREADY_STARTED", "This arena has already started.");
        }
        arena.start();
        arenas.save(arena);
        ArenaQuestion first = publishQuestion(arena, 1);
        ArenaView view = toView(arena, toQuestionView(arena, first));
        broker.convertAndSend(topic(arena.getId(), "state"), view);
        return view;
    }

    @Transactional
    public ArenaView advance(Long instructorId, Long arenaId) {
        Arena arena = requireOwnedArena(arenaId, instructorId);
        if (arena.getStatus() != ArenaStatus.RUNNING) {
            throw new ValidationException("NOT_RUNNING", "This arena is not currently running.");
        }

        List<ArenaQuestion> all = questions.findByArenaIdOrderByPosition(arenaId);
        Optional<ArenaQuestion> current = all.stream().filter(ArenaQuestion::isOpen).findFirst();
        current.ifPresent(q -> {
            q.close();
            questions.save(q);
        });

        int nextPosition = current.map(q -> q.getPosition() + 1).orElse(1);
        Optional<ArenaQuestion> next = all.stream().filter(q -> q.getPosition() == nextPosition).findFirst();

        if (next.isEmpty()) {
            return end(instructorId, arenaId);
        }

        ArenaQuestion published = publishQuestion(arena, nextPosition);
        ArenaView view = toView(arena, toQuestionView(arena, published));
        broker.convertAndSend(topic(arenaId, "state"), view);
        return view;
    }

    @Transactional
    public ArenaView end(Long instructorId, Long arenaId) {
        Arena arena = requireOwnedArena(arenaId, instructorId);
        arena.end();
        arenas.save(arena);

        List<ArenaParticipant> ranked = participants.findByArenaIdOrderByScoreDesc(arenaId);
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setFinalRank(i + 1);
        }
        participants.saveAll(ranked);

        ArenaView view = toView(arena, null);
        broker.convertAndSend(topic(arenaId, "state"), view);
        broadcastLeaderboard(arenaId);
        log.info("Arena {} ended — {} participants", arenaId, ranked.size());
        return view;
    }

    @Transactional
    public AnswerResult answer(Long arenaId, Long participantId, Long arenaQuestionId,
                                Long selectedOptionId, Integer responseTimeMs) {
        Arena arena = arenas.findById(arenaId).orElseThrow(() -> new NotFoundException("Arena", arenaId));
        ArenaParticipant participant = participants.findById(participantId)
                .filter(p -> p.getArenaId().equals(arenaId))
                .orElseThrow(() -> new NotFoundException("Participant", participantId));
        ArenaQuestion question = questions.findById(arenaQuestionId)
                .filter(q -> q.getArenaId().equals(arenaId))
                .orElseThrow(() -> new NotFoundException("Question", arenaQuestionId));

        if (!question.isOpen()) {
            throw new ValidationException("QUESTION_CLOSED", "This question is no longer open.");
        }
        if (answers.findByArenaQuestionIdAndParticipantId(arenaQuestionId, participantId).isPresent()) {
            throw new ValidationException("ALREADY_ANSWERED", "Already answered this question.");
        }

        ArenaItemSource.ScoredAnswer scored = itemSource.score(question.getItemId(), selectedOptionId);
        int points = scored.correct() ? points(question.getTimeLimitSec(), responseTimeMs) : 0;

        answers.save(new ArenaAnswer(arenaQuestionId, participantId, selectedOptionId,
                scored.correct(), responseTimeMs, points));
        participant.recordAnswer(scored.correct(), points);
        participants.save(participant);

        broadcastLeaderboard(arenaId);

        if (participant.getUserId() != null) {
            confusionDetection.recordAnswer(participant.getUserId(), arena.getSkillId(), scored.correct(), arenaId);
        }

        return new AnswerResult(scored.correct(), points, participant.getScore(), scored.correctOptionId());
    }

    // ---- helpers ---------------------------------------------------------

    private ArenaQuestion publishQuestion(Arena arena, int position) {
        ArenaQuestion question = questions.findByArenaIdAndPosition(arena.getId(), position)
                .orElseThrow(() -> new NotFoundException("Arena question at position " + position));
        question.publish();
        return questions.save(question);
    }

    private void broadcastLobby(Arena arena) {
        List<ParticipantView> views = participants.findByArenaIdOrderByScoreDesc(arena.getId()).stream()
                .map(this::toParticipantView).toList();
        broker.convertAndSend(topic(arena.getId(), "lobby"), views);
    }

    private void broadcastLeaderboard(Long arenaId) {
        List<ParticipantView> views = participants.findByArenaIdOrderByScoreDesc(arenaId).stream()
                .map(this::toParticipantView).toList();
        broker.convertAndSend(topic(arenaId, "leaderboard"), views);
    }

    private int points(int timeLimitSec, Integer responseTimeMs) {
        if (responseTimeMs == null || timeLimitSec <= 0) {
            return BASE_POINTS;
        }
        double remainingFraction = Math.max(0.0,
                1.0 - (responseTimeMs / 1000.0) / timeLimitSec);
        return BASE_POINTS + (int) Math.round(MAX_SPEED_BONUS * remainingFraction);
    }

    private String generateJoinCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(JOIN_CODE_ALPHABET.charAt(random.nextInt(JOIN_CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (arenas.findByJoinCode(code).isPresent());
        return code;
    }

    private String displayNameFor(Long userId, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return userId != null ? "Learner " + userId : "Guest";
    }

    private String topic(Long arenaId, String channel) {
        return "/topic/arena/" + arenaId + "/" + channel;
    }

    private QuestionView currentQuestionView(Arena arena) {
        return questions.findByArenaIdOrderByPosition(arena.getId()).stream()
                .filter(ArenaQuestion::isOpen)
                .findFirst()
                .map(q -> toQuestionView(arena, q))
                .orElse(null);
    }

    private QuestionView toQuestionView(Arena arena, ArenaQuestion question) {
        ArenaItemSource.ArenaItem item = itemSource.findById(question.getItemId())
                .orElseThrow(() -> new NotFoundException("Item", question.getItemId()));
        return new QuestionView(question.getId(), question.getPosition(), arena.getQuestionCount(),
                item.stem(), item.options(), question.getTimeLimitSec());
    }

    private ArenaView toView(Arena arena, QuestionView currentQuestion) {
        String skillName = skillLookup.findById(arena.getSkillId())
                .map(SkillLookup.SkillInfo::name).orElse("Unknown skill");
        return new ArenaView(arena.getId(), arena.getTitle(), skillName, arena.getJoinCode(),
                arena.getStatus().name(), arena.isAllowGuests(), arena.getQuestionCount(),
                arena.getSecondsPerQuestion(), arena.getParticipantCount(), currentQuestion);
    }

    private ParticipantView toParticipantView(ArenaParticipant p) {
        return new ParticipantView(p.getId(), p.getDisplayName(), p.getScore(),
                p.getCorrectCount(), p.getAnswerCount(), p.getFinalRank());
    }

    private Arena requireArenaByCode(String joinCode) {
        return arenas.findByJoinCode(joinCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Arena with that join code"));
    }

    private Arena requireOwnedArena(Long arenaId, Long instructorId) {
        Arena arena = arenas.findById(arenaId).orElseThrow(() -> new NotFoundException("Arena", arenaId));
        if (!arena.getInstructorId().equals(instructorId)) {
            throw new ForbiddenException("That arena belongs to another instructor.");
        }
        return arena;
    }
}
