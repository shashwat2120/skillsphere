package com.skillsphere.realtime.web;

import com.skillsphere.realtime.internal.ArenaService;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live arena — REST for every command, STOMP ({@code /topic/arena/{id}/*})
 * for the server pushing state to everyone watching. See
 * {@code WebSocketConfig} for why the split is drawn there.
 *
 * <p><b>Join and answer are deliberately public.</b> Arenas exist to let
 * guests take part with no account ({@code allow_guests}) — QR-scan and
 * play, exactly like a lecture-hall quiz. Where a caller happens to be
 * signed in, {@code CurrentUser.get()} still resolves normally (a bearer
 * token on a {@code permitAll} endpoint is still parsed, just not required),
 * which is how a real learner's answers end up tied to their own progress
 * and confusion detection while a guest's do not.
 */
@RestController
@RequestMapping("/api/realtime/arenas")
@RequiredArgsConstructor
@Tag(name = "Live Arena", description = "Real-time quiz sessions — create, join, play, and watch live")
public class ArenaController {

    private final ArenaService arenaService;

    @PostMapping
    @Operation(summary = "Create an arena (instructor)",
            description = "Picks a fixed set of active items for the given skill at creation time — "
                    + "everyone in the room answers the same questions in the same order.")
    public ArenaService.ArenaView create(@Valid @RequestBody RealtimeDtos.CreateArenaRequest request) {
        return arenaService.create(CurrentUser.requireId(), request.title(), request.skillId(),
                request.questionCount(), request.secondsPerQuestion());
    }

    @GetMapping("/{joinCode}")
    @Operation(summary = "Preview an arena by its join code",
            description = "Public — a guest sees this before deciding to join, with no account required.")
    public ArenaService.ArenaView preview(@PathVariable String joinCode) {
        return arenaService.findByJoinCode(joinCode);
    }

    @GetMapping("/id/{arenaId}")
    @Operation(summary = "The instructor's own view of an arena they created",
            description = "Unlike the join-code preview, this is owner-only — it's the control panel's "
                    + "data source, not something a participant should be able to reach by guessing an id.")
    public ArenaService.ArenaView forInstructor(@PathVariable Long arenaId) {
        return arenaService.findForInstructor(CurrentUser.requireId(), arenaId);
    }

    @PostMapping("/{joinCode}/join")
    @Operation(summary = "Join an arena",
            description = "guestToken identifies a returning guest across reconnects — the client "
                    + "generates and persists it locally. Rejoining with the same token (or, for a "
                    + "signed-in learner, the same account) returns the same participant rather than "
                    + "creating a duplicate.")
    public ArenaService.ParticipantView join(
            @PathVariable String joinCode,
            @RequestBody RealtimeDtos.JoinRequest request) {
        Long userId = CurrentUser.get().map(p -> p.id()).orElse(null);
        return arenaService.join(joinCode, userId, request.guestToken(), request.displayName());
    }

    @GetMapping("/{arenaId}/leaderboard")
    @Operation(summary = "Current standings",
            description = "REST fallback for the initial page load — after that, the same data arrives "
                    + "live over /topic/arena/{id}/leaderboard as answers come in.")
    public java.util.List<ArenaService.ParticipantView> leaderboard(@PathVariable Long arenaId) {
        return arenaService.leaderboard(arenaId);
    }

    @PostMapping("/{arenaId}/start")
    @Operation(summary = "Start the arena and publish the first question (instructor)")
    public ArenaService.ArenaView start(@PathVariable Long arenaId) {
        return arenaService.start(CurrentUser.requireId(), arenaId);
    }

    @PostMapping("/{arenaId}/advance")
    @Operation(summary = "Close the current question and publish the next, or end the arena (instructor)")
    public ArenaService.ArenaView advance(@PathVariable Long arenaId) {
        return arenaService.advance(CurrentUser.requireId(), arenaId);
    }

    @PostMapping("/{arenaId}/end")
    @Operation(summary = "End the arena early and compute final ranks (instructor)")
    public ArenaService.ArenaView end(@PathVariable Long arenaId) {
        return arenaService.end(CurrentUser.requireId(), arenaId);
    }

    @PostMapping("/{arenaId}/answer")
    @Operation(summary = "Submit an answer to the currently open question",
            description = "One answer per participant per question, enforced at the database — a "
                    + "flaky connection retrying a submit cannot double-score.")
    public ArenaService.AnswerResult answer(
            @PathVariable Long arenaId,
            @Valid @RequestBody RealtimeDtos.AnswerRequest request) {
        return arenaService.answer(arenaId, request.participantId(), request.arenaQuestionId(),
                request.selectedOptionId(), request.responseTimeMs());
    }
}
