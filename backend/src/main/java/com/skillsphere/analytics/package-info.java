/**
 * Analytics: the learning event stream, daily rollups, item statistics and
 * at-risk scoring.
 *
 * <p>A downstream consumer that tolerates lag by design. Nothing on the learner
 * path may ever block waiting for analytics, so every write here arrives by
 * event and is processed asynchronously.
 *
 * <p>Risk scoring weights the first two weeks after enrolment deliberately:
 * that window is where half of all dropouts occur.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"shared", "assessment", "skill"})
package com.skillsphere.analytics;
