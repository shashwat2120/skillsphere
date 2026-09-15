package com.skillsphere.shared.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Identity only — the smallest base every persistent entity can share.
 *
 * <p>The hierarchy is deliberately split three ways because the schema is:
 * append-only tables such as {@code responses}, {@code evidence} and
 * {@code process_events} carry no {@code updated_at} by design, since a
 * mutable audit record is not an audit record. Putting a modification
 * timestamp on this class would force a column onto tables that must never
 * have one, and {@code ddl-auto: validate} would reject it at startup.
 *
 * @see AuditableEntity for rows that are updated in place
 * @see VersionedEntity for rows with genuine concurrent writers
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public boolean isNew() {
        return id == null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Hibernate proxies make getClass() comparisons unreliable here, so
        // compare ids and let repositories keep unrelated types apart.
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        // A transient entity is equal only to itself. Without this, several
        // unsaved entities all compare equal on a null id and collapse into a
        // single element inside any Set.
        return id != null && Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        // Constant by design: the id is null before persistence, so a hash
        // derived from it would change on save and the entity would be lost
        // inside any hash-based collection it had already been added to.
        return getClass().hashCode();
    }
}
