/**
 * Gamification: XP, levels, streaks, badges, quests and seasonal leaderboards.
 *
 * <p>A pure event consumer. It listens for evidence being issued, skills being
 * mastered and vivas being passed, and never calls into those modules — which
 * is what keeps the reward layer from ever slowing the learning path.
 *
 * <p>The product rule it enforces: XP is earned only for verified progress,
 * never for activity. If points could be farmed by clicking, the leaderboard
 * would measure clicking, and that dishonesty would leak straight into the
 * credibility of the passport.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Gamification",
        allowedDependencies = {"shared"})
package com.skillsphere.gamification;
