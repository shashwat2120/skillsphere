package com.skillsphere.verification;

import java.time.Instant;
import java.util.List;

/**
 * Verification's public API for other modules — currently just career, for
 * the skill passport.
 *
 * <p>Returns flattened records rather than the {@code Evidence} JPA entity
 * for the same reason {@code SkillLookup} does: a mapped entity crossing a
 * module boundary drags its persistence context along, and this is exactly
 * the shape an HTTP response from a future evidence service would carry
 * unchanged.
 */
public interface EvidenceLookup {

    /**
     * @param sourceType   what produced this evidence — a viva session, a
     *                     project submission, an instructor review
     * @param sourceTitle  human-readable label for the source, resolved here
     *                     so career never has to reach into verification's
     *                     tables to explain what a source id refers to
     */
    record EvidenceItem(Long id, Long skillId, String evidenceType, String sourceType,
                         Long sourceId, String sourceTitle, double weight, double score,
                         Instant verifiedAt) {
    }

    /** Every evidence row for this learner, most recent first. */
    List<EvidenceItem> findByUserId(Long userId);
}
