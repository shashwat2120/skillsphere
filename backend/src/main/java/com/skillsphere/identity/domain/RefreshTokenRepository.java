package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks up by hash regardless of state.
     *
     * <p>Deliberately returns revoked and expired rows too. Filtering them out
     * here would be a security bug: reuse detection depends on recognising an
     * already-rotated token when it is presented, and a query that hides
     * revoked rows would report "not found" and silently discard the strongest
     * signal of token theft we have.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every live token for a user — used on password change, on
     * suspension, and when a rotation chain is found to be compromised.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now, t.revokedReason = :reason
             where t.user.id = :userId
               and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("now") Instant now,
                         @Param("reason") String reason);

    /** Housekeeping: expired tokens carry no value and should not accumulate. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
