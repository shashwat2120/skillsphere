package com.skillsphere.content.domain;

/**
 * Publication lifecycle.
 *
 * <p>Archived is separate from draft for a reason that only shows up later: a
 * course with learners partway through cannot simply be unpublished. Archiving
 * hides it from the catalogue and blocks new enrolment while leaving existing
 * learners able to finish. Dropping back to DRAFT would strand them mid-course
 * through no fault of their own.
 */
public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
