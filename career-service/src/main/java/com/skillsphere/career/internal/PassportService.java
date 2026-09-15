package com.skillsphere.career.internal;

import com.skillsphere.career.domain.CareerRole;
import com.skillsphere.career.domain.CareerRoleRepository;
import com.skillsphere.career.domain.PassportSnapshot;
import com.skillsphere.career.domain.PassportSnapshotRepository;
import com.skillsphere.shared.error.NotFoundException;
import com.skillsphere.shared.error.ValidationException;
import com.skillsphere.skill.SkillLookup;
import com.skillsphere.verification.EvidenceLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the skill passport — the page where every percentage is clickable
 * and backed by evidence, because that is the entire pitch of the product.
 *
 * <p><b>The trust hierarchy, made concrete.</b> A skill can carry two kinds
 * of proof: diagnostic mastery (Bayesian Knowledge Tracing, continuously
 * updated from every response) and verification evidence (a project defended
 * live under questioning, frozen at the moment it was verified). When both
 * exist, verification wins as the headline number — it is the stronger
 * claim, the one that survived being probed rather than only being
 * consistent across quiz answers. Diagnostic mastery is never hidden, only
 * demoted to supporting detail, because "how much practice backs this" is
 * still worth showing.
 *
 * <p><b>Live view vs. snapshot.</b> {@link #current} recomputes from today's
 * data every time — correct for the learner's own page, where a stale number
 * would be actively misleading. {@link #share} freezes that view into a
 * {@link PassportSnapshot} instead, because a link handed to an employer
 * needs to keep showing what was true when it was shared, not silently
 * change (or worse, quietly improve) underneath them later.
 */
@Service
@RequiredArgsConstructor
public class PassportService {

    private final SkillLookup skillLookup;
    private final EvidenceLookup evidenceLookup;
    private final CareerRoleRepository careerRoles;
    private final PassportSnapshotRepository snapshots;
    private final GapAnalysisService gapAnalysis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public record SkillEntry(Long skillId, String skillName, Double diagnosticMastery,
                              Integer diagnosticResponses, List<EvidenceLookup.EvidenceItem> evidence,
                              double displayScore, String basis) {
    }

    public record PassportView(List<SkillEntry> skills, int verifiedSkillCount,
                                int totalEvidenceCount, Instant generatedAt) {
    }

    public record SnapshotView(Long snapshotId, String shareToken, PassportView passport,
                                Double readinessScore, String roleTitle, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public PassportView current(Long userId) {
        return build(userId);
    }

    @Transactional
    public SnapshotView share(Long userId, Long careerRoleId) {
        PassportView view = build(userId);
        if (view.skills().isEmpty()) {
            throw new ValidationException("NOTHING_TO_SHARE",
                    "There's nothing on the passport yet — attempt a diagnostic or defend a project first.");
        }

        CareerRole role = careerRoleId == null ? null : careerRoles.findById(careerRoleId)
                .orElseThrow(() -> new NotFoundException("Career role", careerRoleId));
        Double readiness = role == null ? null : gapAnalysis.analyze(userId, role).readinessScore();

        String token = generateToken();
        PassportSnapshot snapshot = new PassportSnapshot(userId, careerRoleId, toJson(view),
                readiness == null ? null : BigDecimal.valueOf(readiness).setScale(2, RoundingMode.HALF_UP),
                view.totalEvidenceCount());
        snapshot.setShareToken(token);
        snapshot = snapshots.save(snapshot);

        return new SnapshotView(snapshot.getId(), token, view, readiness,
                role == null ? null : role.getTitle(), snapshot.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public SnapshotView publicView(String shareToken) {
        PassportSnapshot snapshot = snapshots.findByShareToken(shareToken)
                .orElseThrow(() -> new NotFoundException("Shared passport"));
        if (snapshot.isExpired()) {
            throw new NotFoundException("Shared passport");
        }
        PassportView view = fromJson(snapshot.getSnapshot());
        String roleTitle = snapshot.getCareerRoleId() == null ? null
                : careerRoles.findById(snapshot.getCareerRoleId()).map(CareerRole::getTitle).orElse(null);
        return new SnapshotView(snapshot.getId(), snapshot.getShareToken(), view,
                snapshot.getReadinessScore() == null ? null : snapshot.getReadinessScore().doubleValue(),
                roleTitle, snapshot.getCreatedAt());
    }

    private PassportView build(Long userId) {
        Map<Long, EvidenceLookup.EvidenceItem> latestEvidenceBySkill = new LinkedHashMap<>();
        List<EvidenceLookup.EvidenceItem> allEvidence = evidenceLookup.findByUserId(userId);
        // findByUserId is already most-recent-first, so the first item seen
        // per skill is that skill's latest — no separate sort needed.
        Map<Long, List<EvidenceLookup.EvidenceItem>> evidenceBySkill = new LinkedHashMap<>();
        for (EvidenceLookup.EvidenceItem item : allEvidence) {
            evidenceBySkill.computeIfAbsent(item.skillId(), k -> new java.util.ArrayList<>()).add(item);
            latestEvidenceBySkill.putIfAbsent(item.skillId(), item);
        }

        List<SkillLookup.SkillInfo> allSkills = skillLookup.findAllActive();
        List<Long> allSkillIds = allSkills.stream().map(SkillLookup.SkillInfo::id).toList();
        Map<Long, SkillLookup.MasteryInfo> mastery = skillLookup.masteryOf(userId, allSkillIds);

        List<SkillEntry> entries = allSkills.stream()
                // A learner_skill_state row exists for every skill from the
                // moment it's provisioned (Phase 2), all starting at the same
                // uninformed BKT prior — so its mere presence proves nothing.
                // "Has evidence" means a real response was recorded, or
                // verification evidence exists; a provisioned-but-untouched
                // row is exactly the "no evidence" case this page exists to
                // keep off the passport.
                .filter(s -> hasRealMastery(mastery.get(s.id())) || evidenceBySkill.containsKey(s.id()))
                .map(s -> toEntry(s, mastery.get(s.id()), evidenceBySkill.get(s.id()), latestEvidenceBySkill.get(s.id())))
                .toList();

        int verifiedCount = (int) entries.stream().filter(e -> "verified".equals(e.basis())).count();
        return new PassportView(entries, verifiedCount, allEvidence.size(), Instant.now());
    }

    private SkillEntry toEntry(SkillLookup.SkillInfo skill, SkillLookup.MasteryInfo masteryInfo,
                                List<EvidenceLookup.EvidenceItem> evidenceForSkill,
                                EvidenceLookup.EvidenceItem latest) {
        List<EvidenceLookup.EvidenceItem> evidence = evidenceForSkill == null ? List.of() : evidenceForSkill;
        // A provisioned-but-untouched row (0 responses) carries only the
        // uninformed prior — treated as absent, the same as no row at all.
        boolean hasMastery = hasRealMastery(masteryInfo);
        Double diagnosticMastery = hasMastery ? masteryInfo.masteryProbability() : null;
        Integer responses = hasMastery ? masteryInfo.responseCount() : null;

        double displayScore;
        String basis;
        if (latest != null) {
            displayScore = latest.score();
            basis = "verified";
        } else if (diagnosticMastery != null) {
            displayScore = diagnosticMastery;
            basis = "diagnostic";
        } else {
            displayScore = 0.0;
            basis = "none";
        }

        return new SkillEntry(skill.id(), skill.name(), diagnosticMastery, responses, evidence,
                round(displayScore), basis);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toJson(PassportView view) {
        return objectMapper.writeValueAsString(view);
    }

    private PassportView fromJson(String json) {
        return objectMapper.readValue(json, PassportView.class);
    }

    private boolean hasRealMastery(SkillLookup.MasteryInfo info) {
        return info != null && info.responseCount() > 0;
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
