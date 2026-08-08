package com.greenhouse.backend.audit.repository;

import com.greenhouse.backend.audit.domain.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
}
