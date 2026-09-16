package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    List<MfaRecoveryCode> findByUserIdAndUsedAtIsNull(Long userId);

    /** Re-enrollment retires every earlier batch — an old code must not outlive its secret. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MfaRecoveryCode c where c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
