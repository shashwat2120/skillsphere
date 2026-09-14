package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Retires any outstanding tokens before a new one is issued.
     *
     * <p>Without this, requesting a reset three times leaves three live keys to
     * the account scattered across an inbox. Only the most recent request should
     * be able to change the password.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update PasswordResetToken t
              set t.usedAt = :now
            where t.user.id = :userId
              and t.usedAt is null
           """)
    int invalidateOutstanding(@Param("userId") Long userId, @Param("now") Instant now);
}
