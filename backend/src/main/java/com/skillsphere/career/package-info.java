/**
 * Career: role catalogue, gap analysis, learning path generation and the skill
 * passport.
 *
 * <p>Owns the explainability trace. Every generated path step records why it
 * was chosen at the moment it was chosen — reconstructing that reasoning later
 * would produce a plausible story rather than a record.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Career and Paths",
        allowedDependencies = {"shared", "skill", "content", "assessment"})
package com.skillsphere.career;
