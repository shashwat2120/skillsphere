package com.skillsphere.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(Long userId);

    Optional<UserDevice> findByUserIdAndUserAgent(Long userId, String userAgent);
}
