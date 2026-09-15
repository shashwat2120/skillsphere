package com.skillsphere.verification.domain;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ProjectSkillId implements Serializable {

    private Long projectId;
    private Long skillId;

    public ProjectSkillId(Long projectId, Long skillId) {
        this.projectId = projectId;
        this.skillId = skillId;
    }
}
