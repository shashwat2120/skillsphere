package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Roles are reference data seeded by migration, so this is read-only in
 * practice. A missing role means the database was not migrated, which is a
 * startup fault rather than something to handle at runtime.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
