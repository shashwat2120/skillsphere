package com.skillsphere.realtime.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Live arena standings — a Redis sorted set per arena, not a Postgres query.
 *
 * <p>{@code arena_participants.score} (see {@code ArenaService}) stays the
 * durable record; this is the fast, high-churn read path a per-answer live
 * leaderboard actually needs. Key is {@code arena:{arenaId}:leaderboard},
 * member is the participant id, score is their cumulative points — a plain
 * {@code ZADD} on every scored answer, since the caller always has the
 * authoritative running total from the Postgres write it just made, rather
 * than a {@code ZINCRBY} that would have to agree with Postgres on the delta.
 *
 * <p><b>Cold or unavailable Redis fails open to "no ranking here."</b> An
 * arena that hasn't had its first scored answer yet has an empty sorted set,
 * and a Redis outage looks the same to a caller as "empty" — either way
 * {@link ArenaService} falls back to the Postgres {@code ORDER BY}, which is
 * always correct, just not the fast path. Nothing here throws.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArenaLeaderboardService {

    private static final String KEY_PREFIX = "arena:";
    private static final String KEY_SUFFIX = ":leaderboard";

    private final StringRedisTemplate redis;

    public record ScoreEntry(Long participantId, int score) {
    }

    /** Sets a participant's live score to the given (already-authoritative) total. */
    public void recordScore(Long arenaId, Long participantId, int score) {
        try {
            redis.opsForZSet().add(key(arenaId), participantId.toString(), score);
        } catch (Exception ex) {
            log.warn("Could not update live leaderboard for arena {} — Postgres stays authoritative", arenaId, ex);
        }
    }

    /** Highest score first. Empty when the arena has no scored answers yet, or Redis is unreachable. */
    public List<ScoreEntry> top(Long arenaId) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(key(arenaId), 0, -1);
            if (tuples == null || tuples.isEmpty()) {
                return Collections.emptyList();
            }
            return tuples.stream()
                    .filter(t -> t.getValue() != null && t.getScore() != null)
                    .map(t -> new ScoreEntry(Long.valueOf(t.getValue()), t.getScore().intValue()))
                    .toList();
        } catch (Exception ex) {
            log.warn("Could not read live leaderboard for arena {} — falling back to Postgres", arenaId, ex);
            return Collections.emptyList();
        }
    }

    /** Called when an arena ends — the live sorted set has no further reason to exist. */
    public void clear(Long arenaId) {
        try {
            redis.delete(key(arenaId));
        } catch (Exception ex) {
            log.warn("Could not clear live leaderboard for arena {}", arenaId, ex);
        }
    }

    private String key(Long arenaId) {
        return KEY_PREFIX + arenaId + KEY_SUFFIX;
    }
}
