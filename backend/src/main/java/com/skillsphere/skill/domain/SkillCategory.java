package com.skillsphere.skill.domain;

import com.skillsphere.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A grouping for skills — "Java", "Databases", "Communication".
 *
 * <p>Purely presentational. Categories carry no meaning for the engine: routing
 * follows prerequisite edges, never category membership. Two skills sharing a
 * category implies nothing about the order they should be learned in, which is
 * exactly why the graph exists separately.
 */
@Entity
@Table(name = "skill_categories")
@Getter
@Setter
@NoArgsConstructor
public class SkillCategory extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;

    /** Hex colour used by the skill-tree view to tint a branch. */
    @Column(length = 7)
    private String colour;

    @Column(nullable = false)
    private int position = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
