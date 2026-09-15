package com.skillsphere.content.internal;

import com.skillsphere.content.domain.*;
import com.skillsphere.content.web.ContentDtos;
import com.skillsphere.shared.error.ConflictException;
import com.skillsphere.shared.error.ForbiddenException;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Course authoring.
 *
 * <p>Ownership is enforced here rather than by URL or role. The rule is about
 * the resource, not the route — an instructor may edit courses, but only their
 * own — and role-based rules cannot express that. Putting the check in the
 * controller would mean every future caller has to remember to repeat it, which
 * is how one forgotten endpoint becomes the hole that lets one instructor
 * rewrite another's material.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonSkillRepository lessonSkills;
    private final SkillRef skillRef;

    // -----------------------------------------------------------------
    // Courses
    // -----------------------------------------------------------------

    @Transactional
    public ContentDtos.CourseResponse create(ContentDtos.CreateCourseRequest request, Long instructorId) {
        if (courses.existsBySlug(request.slug())) {
            throw new ConflictException("DUPLICATE_SLUG", "A course with that slug already exists.");
        }

        Course course = new Course(request.title(), request.slug(), instructorId);
        course.setSubtitle(request.subtitle());
        course.setDescription(request.description());
        course.setCategoryId(request.categoryId());
        if (request.levelBand() != null) {
            course.setLevelBand(request.levelBand());
        }

        log.info("Course '{}' created by instructor {}", request.title(), instructorId);
        return toResponse(courses.save(course));
    }

    /**
     * Publishes a course, refusing to publish something with nothing in it.
     *
     * <p>An empty published course is worse than no course: it appears in the
     * catalogue, a learner enrols, and finds nothing. The check costs one query
     * and prevents the most common authoring mistake.
     */
    @Transactional
    public void publish(Long courseId, Long userId, boolean isAdmin) {
        Course course = requireOwned(courseId, userId, isAdmin);

        if (modules.countByCourseId(courseId) == 0) {
            throw new ValidationException("EMPTY_COURSE",
                    "Add at least one module before publishing.");
        }

        long taggedLessons = modules.findByCourseIdOrderByPosition(courseId).stream()
                .flatMap(module -> lessons.findByModuleIdOrderByPosition(module.getId()).stream())
                .filter(lesson -> !lessonSkills.findByLessonId(lesson.getId()).isEmpty())
                .count();

        if (taggedLessons == 0) {
            // A warning would be too weak here. A course whose lessons teach no
            // recorded skill is invisible to the path engine — it can only ever
            // be found by browsing, which makes it a course on a shelf rather
            // than part of the product.
            throw new ValidationException("NO_SKILLS_TAGGED",
                    "Tag at least one lesson with the skill it teaches, or the course "
                            + "can never be recommended to anyone.");
        }

        course.publish();
        log.info("Course {} published by user {}", courseId, userId);
    }

    @Transactional
    public void archive(Long courseId, Long userId, boolean isAdmin) {
        // Archiving rather than unpublishing: existing learners keep access and
        // only new enrolment stops. Dropping a course back to draft would strand
        // people partway through for a decision that was not theirs.
        requireOwned(courseId, userId, isAdmin).archive();
    }

    // -----------------------------------------------------------------
    // Structure
    // -----------------------------------------------------------------

    @Transactional
    public ContentDtos.ModuleResponse addModule(Long courseId, ContentDtos.CreateModuleRequest request,
                                                Long userId, boolean isAdmin) {
        Course course = requireOwned(courseId, userId, isAdmin);
        int position = (int) modules.countByCourseId(courseId);

        CourseModule module = new CourseModule(course, request.title(), position);
        module.setSummary(request.summary());
        modules.save(module);

        return new ContentDtos.ModuleResponse(
                module.getId(), module.getTitle(), module.getSummary(), module.getPosition(), List.of());
    }

    @Transactional
    public ContentDtos.LessonResponse addLesson(Long moduleId, ContentDtos.CreateLessonRequest request,
                                                Long userId, boolean isAdmin) {
        CourseModule module = modules.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("Module", moduleId));
        requireOwned(module.getCourse().getId(), userId, isAdmin);

        int position = lessons.findByModuleIdOrderByPosition(moduleId).size();

        Lesson lesson = new Lesson(module, request.title(), position);
        lesson.setType(request.type() == null ? LessonType.TEXT : request.type());
        lesson.setContent(request.content());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setResourceUrl(request.resourceUrl());
        if (request.durationSeconds() != null) {
            lesson.setDurationSeconds(request.durationSeconds());
        }
        if (request.preview() != null) {
            lesson.setPreview(request.preview());
        }
        lessons.save(lesson);

        return toLessonResponse(lesson, Map.of());
    }

    /**
     * Tags a lesson with a skill it teaches.
     *
     * <p>The bridge between the two halves of the system. Until a lesson is
     * tagged, the content module and the skill graph are two disconnected
     * systems: the engine knows someone needs Collections and has no way to find
     * anything that teaches it.
     */
    @Transactional
    public void tagSkill(Long lessonId, ContentDtos.TagSkillRequest request,
                         Long userId, boolean isAdmin) {
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson", lessonId));
        requireOwned(lesson.getModule().getCourse().getId(), userId, isAdmin);

        SkillRef.SkillSummary skill = skillRef.findById(request.skillId())
                .orElseThrow(() -> new NotFoundException("Skill", request.skillId()));

        lessonSkills.save(new LessonSkill(lessonId, skill.id(), request.weight()));
        log.info("Lesson {} tagged as teaching '{}' (weight {})",
                lessonId, skill.name(), request.weight());
    }

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ContentDtos.CourseResponse> listPublished() {
        return courses.findPublished().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ContentDtos.CourseResponse> listMine(Long instructorId) {
        return courses.findByInstructor(instructorId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ContentDtos.CourseDetailResponse detail(Long courseId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course", courseId));

        // Skill names are resolved once into a map rather than per lesson. A
        // course with forty lessons would otherwise issue forty lookups to
        // render one page.
        Map<Long, String> skillNames = skillRef.findAllActive().stream()
                .collect(Collectors.toMap(SkillRef.SkillSummary::id, SkillRef.SkillSummary::name, (a, b) -> a));

        List<ContentDtos.ModuleResponse> moduleResponses =
                modules.findByCourseIdOrderByPosition(courseId).stream()
                        .map(module -> new ContentDtos.ModuleResponse(
                                module.getId(), module.getTitle(), module.getSummary(),
                                module.getPosition(),
                                lessons.findByModuleIdOrderByPosition(module.getId()).stream()
                                        .map(lesson -> toLessonResponse(lesson, skillNames))
                                        .toList()))
                        .toList();

        return new ContentDtos.CourseDetailResponse(toResponse(course), moduleResponses);
    }

    /**
     * Lessons that teach a given skill.
     *
     * <p>Called by the path engine when it has decided what a learner should work
     * on and needs something for them to actually open.
     */
    @Transactional(readOnly = true)
    public List<ContentDtos.LessonResponse> findLessonsTeaching(Long skillId) {
        Map<Long, String> names = skillRef.findAllActive().stream()
                .collect(Collectors.toMap(SkillRef.SkillSummary::id, SkillRef.SkillSummary::name, (a, b) -> a));
        return lessons.findTeachingSkill(skillId).stream()
                .map(lesson -> toLessonResponse(lesson, names))
                .toList();
    }

    // -----------------------------------------------------------------

    private Course requireOwned(Long courseId, Long userId, boolean isAdmin) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course", courseId));
        if (!isAdmin && !course.isOwnedBy(userId)) {
            throw new ForbiddenException("You can only modify courses you own.");
        }
        return course;
    }

    private ContentDtos.CourseResponse toResponse(Course course) {
        return new ContentDtos.CourseResponse(
                course.getId(), course.getTitle(), course.getSlug(), course.getSubtitle(),
                course.getDescription(), course.getInstructorId(), course.getLevelBand(),
                course.getStatus(), course.getEstMinutes(), course.getPublishedAt(),
                (int) modules.countByCourseId(course.getId()));
    }

    private ContentDtos.LessonResponse toLessonResponse(Lesson lesson, Map<Long, String> skillNames) {
        List<ContentDtos.SkillTag> tags = lessonSkills.findByLessonId(lesson.getId()).stream()
                .map(link -> new ContentDtos.SkillTag(
                        link.getSkillId(),
                        skillNames.getOrDefault(link.getSkillId(), "?"),
                        link.getWeight()))
                .toList();

        return new ContentDtos.LessonResponse(
                lesson.getId(), lesson.getTitle(), lesson.getType(), lesson.getContent(),
                lesson.getVideoUrl(), lesson.getResourceUrl(), lesson.getDurationSeconds(),
                lesson.getPosition(), lesson.isPreview(), tags);
    }
}
