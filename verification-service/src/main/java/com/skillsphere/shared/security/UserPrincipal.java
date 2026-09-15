package com.skillsphere.shared.security;

import java.io.Serializable;
import java.util.List;

/**
 * The authenticated caller, as reconstructed from a token.
 *
 * <p>Carries an id, an address for display and logging, and roles — nothing
 * more. It is not a {@code User} entity and must never become one: attaching a
 * JPA entity to the security context would drag a detached object through every
 * request, invite lazy-loading failures outside a transaction, and couple every
 * module that reads the current user to identity's internals.
 *
 * <p>Authorisation always uses {@link #id()}. The email is mutable and is never
 * an identity.
 */
public record UserPrincipal(Long id, String email, List<String> roles) implements Serializable {

    public boolean hasRole(String role) {
        return roles.contains(role.startsWith("ROLE_") ? role : "ROLE_" + role);
    }
}
