package com.skillsphere.content.web;

import com.skillsphere.content.internal.CourseService;
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
 * Course authoring.
 *
 * <p>Role is checked here; <em>ownership</em> is checked in the service. The
 * split matters: "is an instructor" is a property of the caller and belongs at
 * the boundary, while "owns this course" is a property of the resource and has
 * to travel with the operation.
 */
@RestController
@RequestMapping("/api/instructor/courses")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Course authoring", description = "Building courses, modules and lessons")
public class InstructorCourseController {

    private final CourseService courseService;

    @PostMapping
    @Operation(summary = "Create a course")
    public ResponseEntity<ContentDtos.CourseResponse> create(
            @Valid @RequestBody ContentDtos.CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.create(request, CurrentUser.requireId()));
    }

    @GetMapping
    @Operation(summary = "Courses you own")
    public List<ContentDtos.CourseResponse> mine() {
        return courseService.listMine(CurrentUser.requireId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "A course with its full structure")
    public ContentDtos.CourseDetailResponse detail(@PathVariable Long id) {
        return courseService.detail(id);
    }

    @PostMapping("/{id}/modules")
    @Operation(summary = "Add a module")
    public ResponseEntity<ContentDtos.ModuleResponse> addModule(
            @PathVariable Long id, @Valid @RequestBody ContentDtos.CreateModuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                courseService.addModule(id, request, CurrentUser.requireId(),
                        CurrentUser.hasRole("ADMIN")));
    }

    @PostMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "Add a lesson",
            description = "Video lessons take an embed URL — the platform never hosts video.")
    public ResponseEntity<ContentDtos.LessonResponse> addLesson(
            @PathVariable Long moduleId, @Valid @RequestBody ContentDtos.CreateLessonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                courseService.addLesson(moduleId, request, CurrentUser.requireId(),
                        CurrentUser.hasRole("ADMIN")));
    }

    @PostMapping("/lessons/{lessonId}/skills")
    @Operation(summary = "Tag a lesson with a skill it teaches",
            description = "The bridge between content and the skill graph. An untagged lesson can "
                    + "be browsed but never recommended, because the engine cannot say which gap "
                    + "it would close.")
    public ResponseEntity<Void> tagSkill(
            @PathVariable Long lessonId, @Valid @RequestBody ContentDtos.TagSkillRequest request) {
        courseService.tagSkill(lessonId, request, CurrentUser.requireId(),
                CurrentUser.hasRole("ADMIN"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a course",
            description = "Refused if the course is empty, or if no lesson is tagged with a skill — "
                    + "an untagged course can never be recommended and would sit on a shelf.")
    public ResponseEntity<Void> publish(@PathVariable Long id) {
        courseService.publish(id, CurrentUser.requireId(), CurrentUser.hasRole("ADMIN"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a course",
            description = "Stops new enrolment while existing learners keep access.")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        courseService.archive(id, CurrentUser.requireId(), CurrentUser.hasRole("ADMIN"));
        return ResponseEntity.noContent().build();
    }
}
