package com.skillsphere.identity.domain;

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
 * A role, seeded by migration rather than created at runtime.
 *
 * <p>Roles are reference data: the four that exist are the four that will ever
 * exist, and an application that can mint new roles is an application whose
 * authorisation rules cannot be reasoned about.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Spring Security expects authorities prefixed with ROLE_. */
    public String authority() {
        return "ROLE_" + name.name();
    }
}
