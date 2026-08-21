package com.greenhouse.backend.audit.repository;

import com.greenhouse.backend.audit.domain.AuditEventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

	@Query("""
			select event from AuditEventEntity event
			where event.entityType = :entityType
			  and event.id > :afterId
			  and event.occurredAt <= :sourceCutoff
			order by event.id
			""")
	List<AuditEventEntity> findHistoryAfter(
			@Param("entityType") String entityType,
			@Param("afterId") long afterId,
			@Param("sourceCutoff") Instant sourceCutoff,
			Pageable pageable);
}
