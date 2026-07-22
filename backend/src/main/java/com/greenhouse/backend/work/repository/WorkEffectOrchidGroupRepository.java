package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkEffectOrchidGroupRepository extends JpaRepository<WorkEffectOrchidGroup, Long> {

	List<WorkEffectOrchidGroup> findByWorkAppliedEffectIdOrderByIdAsc(Long workAppliedEffectId);

	@EntityGraph(attributePaths = "workAppliedEffect")
	List<WorkEffectOrchidGroup> findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = "workAppliedEffect")
	List<WorkEffectOrchidGroup> findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(
			Long workOperationId,
			WorkEffectOrchidGroupRelationType relationType);

	@EntityGraph(attributePaths = {"workAppliedEffect", "workAppliedEffect.workOperation", "workAppliedEffect.workOperation.workType"})
	List<WorkEffectOrchidGroup> findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
			Long orchidGroupId);

	@EntityGraph(attributePaths = {"workAppliedEffect", "workAppliedEffect.workOperation", "workAppliedEffect.workOperation.workType"})
	List<WorkEffectOrchidGroup> findByOrchidGroupIdInOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
			Collection<Long> orchidGroupIds);

	@EntityGraph(attributePaths = {"workAppliedEffect", "workAppliedEffect.workOperation", "workAppliedEffect.workOperation.workType"})
	List<WorkEffectOrchidGroup> findByWorkAppliedEffectWorkOperationIdInAndOrchidGroupIdInOrderByWorkAppliedEffectWorkOperationIdAscIdAsc(
			Collection<Long> workOperationIds,
			Collection<Long> orchidGroupIds);

	boolean existsByOrchidGroupId(Long orchidGroupId);
}
