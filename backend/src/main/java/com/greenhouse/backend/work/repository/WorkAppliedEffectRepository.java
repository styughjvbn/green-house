package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkAppliedEffectRepository extends JpaRepository<WorkAppliedEffect, Long> {

	long countByWorkOperationIdAndTargetId(Long workOperationId, Long targetId);

	@EntityGraph(attributePaths = "target")
	List<WorkAppliedEffect> findByWorkOperationIdOrderByIdAsc(Long workOperationId);

	Optional<WorkAppliedEffect> findByWorkOperationIdAndEffectKey(Long workOperationId, String effectKey);

	@EntityGraph(attributePaths = "workOperation")
	List<WorkAppliedEffect> findByWorkOperationIdInAndEffectKey(Collection<Long> workOperationIds, String effectKey);
}
