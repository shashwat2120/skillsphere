package com.skillsphere.shared.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable page);

    List<AdminAuditLog> findByTargetTypeOrderByCreatedAtDesc(String targetType, Pageable page);
}
