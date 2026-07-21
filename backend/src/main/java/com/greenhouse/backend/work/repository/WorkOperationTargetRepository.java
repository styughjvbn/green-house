package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkOperationTarget;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOperationTargetRepository extends JpaRepository<WorkOperationTarget, Long> {

	long countByOrchidGroupIdInAndWorkOperationIdNot(Collection<Long> orchidGroupIds, Long workOperationId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
			Collection<Long> workOperationIds);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
			Long orchidGroupId);
}
