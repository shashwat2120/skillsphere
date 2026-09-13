package com.skillsphere.skill.web;

import com.skillsphere.skill.internal.SkillGraphService;
import com.skillsphere.skill.internal.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration of the skill graph.
 *
 * <p>Restricted to administrators, and the restriction is not bureaucratic. The
 * graph decides what every learner is offered next, so one careless edge changes
 * the route for everybody at once — and a cycle stops the platform outright.
 * This is the highest-leverage surface in the product, which is why it is the
 * most tightly held.
 *
 * <p>{@code @PreAuthorize} is applied at class level in addition to the URL rule
 * in the security configuration. The duplication is intentional: a future
 * refactor that moves or renames these paths would silently drop the URL-based
 * protection, whereas the annotation travels with the code.
 */
@RestController
@RequestMapping("/api/admin/skills")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Skill graph (admin)", description = "Manage skills and their prerequisite edges")
public class AdminSkillController {

    private final SkillService skillService;
    private final SkillGraphService graphService;

    @PostMapping
    @Operation(summary = "Create a skill")
    public ResponseEntity<SkillDtos.SkillResponse> create(
            @Valid @RequestBody SkillDtos.CreateSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a skill",
            description = "The slug cannot be changed — it is a stable public identifier "
                    + "referenced by evidence records and shared passports.")
    public SkillDtos.SkillResponse update(@PathVariable Long id,
                                          @Valid @RequestBody SkillDtos.UpdateSkillRequest request) {
        return skillService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Retire a skill",
            description = "Deactivates rather than deletes. Evidence and mastery history "
                    + "reference this skill and must outlive its removal from the catalogue.")
    public ResponseEntity<Void> retire(@PathVariable Long id) {
        skillService.retire(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/graph")
    @Operation(summary = "A skill with its immediate neighbours")
    public SkillDtos.SkillGraphNode graphNode(@PathVariable Long id) {
        return skillService.getGraphNode(id);
    }

    @PostMapping("/{id}/prerequisites")
    @Operation(summary = "Add a prerequisite edge",
            description = "Rejected with 422 if the edge would create a cycle. Acyclicity cannot be "
                    + "expressed as a database constraint, so this check is the only guard — and a "
                    + "cycle would stop path generation for every learner on the platform.")
    public ResponseEntity<SkillDtos.PrerequisiteResponse> addPrerequisite(
            @PathVariable Long id,
            @Valid @RequestBody SkillDtos.AddPrerequisiteRequest request) {

        var edge = graphService.addPrerequisite(id, request.prerequisiteSkillId(), request.strength());

        return ResponseEntity.status(HttpStatus.CREATED).body(new SkillDtos.PrerequisiteResponse(
                edge.getId(),
                new SkillDtos.SkillRef(edge.getSkill().getId(), edge.getSkill().getName()),
                new SkillDtos.SkillRef(edge.getPrerequisiteSkill().getId(),
                                       edge.getPrerequisiteSkill().getName()),
                edge.getStrength(),
                edge.isHardGate()));
    }

    @DeleteMapping("/{id}/prerequisites/{prerequisiteId}")
    @Operation(summary = "Remove a prerequisite edge")
    public ResponseEntity<Void> removePrerequisite(@PathVariable Long id,
                                                   @PathVariable Long prerequisiteId) {
        graphService.removePrerequisite(id, prerequisiteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "All active skills")
    public List<SkillDtos.SkillResponse> list() {
        return skillService.listActive();
    }
}
