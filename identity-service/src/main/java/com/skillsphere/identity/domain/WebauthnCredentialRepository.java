package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebauthnCredentialRepository extends JpaRepository<WebauthnCredential, Long> {

    Optional<WebauthnCredential> findByCredentialId(String credentialId);

    List<WebauthnCredential> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
