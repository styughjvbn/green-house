package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkAppliedEffect;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkAppliedEffectRepository extends JpaRepository<WorkAppliedEffect, Long> {

	long countByWorkOperationIdAndTargetId(Long workOperationId, Long targetId);

	List<WorkAppliedEffect> findByWorkOperationIdOrderByIdAsc(Long workOperationId);
}
