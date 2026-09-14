package com.skillsphere.content.web;

import com.skillsphere.content.internal.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The learner-facing catalogue.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Catalogue", description = "Published courses and the lessons that teach a skill")
public class CatalogController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "Published courses")
    public List<ContentDtos.CourseResponse> published() {
        return courseService.listPublished();
    }

    @GetMapping("/{id}")
    @Operation(summary = "A course with its modules and lessons")
    public ContentDtos.CourseDetailResponse detail(@PathVariable Long id) {
        return courseService.detail(id);
    }

    @GetMapping("/teaching")
    @Operation(summary = "Lessons that teach a given skill",
            description = "How the path engine turns 'you are weak at Collections' into something "
                    + "a learner can open. Ordered by how central the skill is to each lesson.")
    public List<ContentDtos.LessonResponse> teaching(@RequestParam Long skillId) {
        return courseService.findLessonsTeaching(skillId);
    }
}
