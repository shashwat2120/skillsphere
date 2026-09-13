/**
 * Content: courses, modules, lessons, enrolment and progress.
 *
 * <p>Depends on skill because content exists to serve skills — every lesson is
 * tagged to what it teaches. The dependency runs in this direction only: the
 * skill graph must remain meaningful with no courses attached to it at all.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Content",
        allowedDependencies = {"shared", "skill"})
package com.skillsphere.content;
