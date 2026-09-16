package com.skillsphere.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One admin-editable platform setting, keyed by name.
 *
 * <p>{@link #value} is stored as JSONB and held here as raw JSON text — this
 * module has no reason to model the shape of every setting an admin might
 * ever add, so the value is opaque to identity and it is the caller's job
 * (the frontend, mainly) to interpret it.
 */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSetting {

    @Id
    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlatformSetting(String key, String valueJson, Long updatedBy) {
        this.key = key;
        this.value = valueJson;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void update(String valueJson, Long updatedBy) {
        this.value = valueJson;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
