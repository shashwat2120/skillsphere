package com.skillsphere.assessment.web;

import com.skillsphere.assessment.internal.ItemBankService;
import com.skillsphere.assessment.internal.MisconceptionService;
import com.skillsphere.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Item authoring.
 *
 * <p>Restricted to instructors and administrators. Items decide what other people
 * are judged on, which makes this the second highest-leverage surface in the
 * product after the skill graph.
 */
@RestController
@RequestMapping("/api/instructor/items")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Item bank", description = "Authoring assessment items and the misconception library")
public class ItemBankController {

    private final ItemBankService itemBank;
    private final MisconceptionService misconceptionService;

    @PostMapping
    @Operation(summary = "Author an item",
            description = "Validated structurally: exactly one correct option, at least two options, "
                    + "and no misconception on the correct answer. A broken item does not fail "
                    + "loudly — it silently produces wrong measurements the engine treats as fact.")
    public ResponseEntity<ItemDtos.ItemResponse> create(
            @Valid @RequestBody ItemDtos.CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemBank.create(request, CurrentUser.requireId()));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Put an item into circulation")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        itemBank.activate(id, CurrentUser.requireId(), CurrentUser.hasRole("ADMIN"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retire")
    @Operation(summary = "Withdraw an item",
            description = "Retires rather than deletes: existing responses are the evidence behind "
                    + "skill claims already issued.")
    public ResponseEntity<Void> retire(@PathVariable Long id) {
        itemBank.retire(id, CurrentUser.requireId(), CurrentUser.hasRole("ADMIN"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Active items for a skill")
    public List<ItemDtos.ItemResponse> listBySkill(@RequestParam Long skillId) {
        return itemBank.listBySkill(skillId);
    }

    @GetMapping("/health")
    @Operation(summary = "Items that look broken",
            description = "Flags items almost everyone gets right, almost nobody gets right, or "
                    + "where strong and weak learners perform equally — the signature of a "
                    + "question whose wording is the obstacle.")
    public List<ItemDtos.ItemHealthResponse> health() {
        return itemBank.findSuspectItems();
    }

    @PostMapping("/misconceptions")
    @Operation(summary = "Add a misconception to the library")
    public ResponseEntity<ItemDtos.MisconceptionResponse> createMisconception(
            @Valid @RequestBody ItemDtos.CreateMisconceptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(misconceptionService.create(request));
    }

    @GetMapping("/misconceptions")
    @Operation(summary = "Misconceptions for a skill")
    public List<ItemDtos.MisconceptionResponse> misconceptions(@RequestParam Long skillId) {
        return misconceptionService.listBySkill(skillId);
    }

    @GetMapping("/misconceptions/most-observed")
    @Operation(summary = "What the cohort gets wrong most",
            description = "A teaching signal rather than a learner one: a belief appearing across "
                    + "many people is being produced by the explanation.")
    public List<ItemDtos.MisconceptionResponse> mostObserved() {
        return misconceptionService.mostObserved();
    }
}
