/**
 * Assessment: the item bank, adaptive item selection, responses and
 * misconception diagnosis.
 *
 * <p>Sits on the hot path — every answered question runs through here — so it
 * stays co-located with the skill engine rather than being split from it. In
 * Sprint 6 these two ship as one service for exactly that reason.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Assessment",
        allowedDependencies = {"shared", "skill"})
package com.skillsphere.assessment;
