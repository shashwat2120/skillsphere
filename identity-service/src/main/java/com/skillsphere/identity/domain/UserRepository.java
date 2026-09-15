package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for accounts.
 *
 * <p>Every lookup filters out soft-deleted rows. Deleted accounts are retained
 * because evidence, submissions and instructor reviews reference them and must
 * outlive the account, but a deleted account must never authenticate — so the
 * filter belongs here rather than being left to each caller to remember.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.email = :email and u.deletedAt is null")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") Long id);

    /**
     * Existence check used during registration.
     *
     * <p>Note this deliberately includes soft-deleted rows: the email column is
     * uniquely indexed regardless of deletion, so ignoring them here would let
     * registration pass validation and then fail on a constraint violation.
     */
    boolean existsByEmail(String email);
}
