package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkOperationTarget;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOperationTargetRepository extends JpaRepository<WorkOperationTarget, Long> {

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = { "workOperation", "workOperation.workType" })
	List<WorkOperationTarget> findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
			Long orchidGroupId);
}
