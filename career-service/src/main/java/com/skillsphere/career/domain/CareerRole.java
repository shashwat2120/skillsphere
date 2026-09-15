package com.skillsphere.career.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A target vector of skills — what replaces "which course do you want to buy?"
 * with "what are you trying to become?".
 *
 * <p>Holds no reference to its required skills as a mapped collection; that
 * lives on {@link RoleSkillRequirement}, one row per (role, skill) pair. A role
 * with forty requirements would otherwise load forty child rows every time
 * anything about the role itself — its title, its icon — is read.
 */
@Entity
@Table(name = "career_roles")
@Getter
@Setter
@NoArgsConstructor
public class CareerRole extends AuditableEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, unique = true, length = 170)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Seniority seniority = Seniority.JUNIOR;

    @Column(length = 50)
    private String icon;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public CareerRole(String title, String slug, Seniority seniority) {
        this.title = title;
        this.slug = slug;
        this.seniority = seniority;
    }
}
