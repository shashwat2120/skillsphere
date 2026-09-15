package com.skillsphere.identity.domain;

import com.skillsphere.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A platform account.
 *
 * <p>Note what this entity does <em>not</em> contain: no learning state, no
 * mastery, no progress, no enrolments. Those live in their own modules and
 * reference this account by id as a plain value. That separation is the single
 * rule protecting the Sprint 6 split — a JPA association reaching in here from
 * the skill engine would become a join across a service boundary, and joins
 * across boundaries are what turn an extraction into a rewrite.
 *
 * <p>Roles are the one exception, because they are genuinely part of identity
 * and are needed on every authenticated request.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {

    /**
     * Stored lower-cased, enforced by a CHECK constraint in the schema.
     * Case-insensitive uniqueness has to be guaranteed by the database rather
     * than by application code, or two accounts differing only in case slip
     * through under concurrent registration.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Argon2id digest. Nullable on purpose: an account created through a
     * passkey or an OAuth provider may never have a password at all, and
     * forcing a placeholder there would mean storing a credential that can be
     * attacked for no reason.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 200)
    private String headline;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(nullable = false, length = 10)
    private String locale = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * Soft delete. Historical evidence, submissions and instructor reviews must
     * survive an account being removed — an employer verifying a passport is
     * looking at records that outlive the account that produced them.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Eagerly fetched because authorisation needs roles on every request, and a
     * user has at most a handful. This is the rare case where EAGER is correct
     * rather than lazy: the alternative is an extra query on literally every
     * authenticated call.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Setter(AccessLevel.NONE)
    private Set<Role> roles = new HashSet<>();

    public User(String email, String fullName) {
        this.email = normaliseEmail(email);
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = normaliseEmail(email);
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public boolean hasRole(RoleName name) {
        return roles.stream().anyMatch(role -> role.getName() == name);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Whether this account may currently authenticate and act.
     *
     * <p>Checked on every request rather than only at login. A suspension has to
     * take effect mid-session — an instructor suspended for abuse who keeps a
     * valid token until it expires is not suspended in any meaningful sense.
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE && deletedAt == null;
    }
}
