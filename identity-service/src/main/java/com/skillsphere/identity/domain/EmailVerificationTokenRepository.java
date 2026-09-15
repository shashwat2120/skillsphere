package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates any outstanding tokens for a user before a new one is issued,
     * so requesting a fresh link silently retires the previous one instead of
     * leaving several valid links alive across an inbox.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationToken t
               set t.usedAt = :now
             where t.user.id = :userId
               and t.usedAt is null
            """)
    int invalidateOutstanding(@Param("userId") Long userId, @Param("now") Instant now);
}
