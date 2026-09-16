package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for TOTP enrollments. The entity id <em>is</em> the user id, so
 * {@link #findById} already answers "does this user have TOTP set up" —
 * no separate {@code findByUserId} is needed.
 */
public interface MfaTotpRepository extends JpaRepository<MfaTotp, Long> {

    /** Used when re-enrolling: a prior unconfirmed attempt must not linger. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MfaTotp t where t.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
