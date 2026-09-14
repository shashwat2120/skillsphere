package com.skillsphere.verification.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A learner's own account of what AI tooling they used.
 *
 * <p>Declared, not banned — banning is unenforceable and poor preparation for
 * real work. What actually gets assessed is judgement about the tool's
 * output, probed in the viva; this record exists so that questioning has
 * something specific to start from.
 */
@Entity
@Table(name = "ai_usage_declarations")
@Getter
@Setter
@NoArgsConstructor
public class AiUsageDeclaration extends BaseEntity {

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "tool_used", length = 100)
    private String toolUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AiUsagePurpose purpose = AiUsagePurpose.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiUsageExtent extent = AiUsageExtent.NONE;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt = Instant.now();

    public AiUsageDeclaration(Long submissionId, String toolUsed, AiUsagePurpose purpose,
                               AiUsageExtent extent, String detail) {
        this.submissionId = submissionId;
        this.toolUsed = toolUsed;
        this.purpose = purpose;
        this.extent = extent;
        this.detail = detail;
    }
}
