package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkTargetExecution;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkTargetExecutionRepository extends JpaRepository<WorkTargetExecution, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select execution
			from WorkTargetExecution execution
			join fetch execution.target target
			join fetch target.workOperation operation
			join fetch operation.workType workType
			where target.inboundRecordId = :inboundRecordId
			  and workType.code = 'POTTING'
			  and operation.status in (
			    com.greenhouse.backend.work.domain.WorkOperationStatus.PLANNED,
			    com.greenhouse.backend.work.domain.WorkOperationStatus.IN_PROGRESS,
			    com.greenhouse.backend.work.domain.WorkOperationStatus.PAUSED
			  )
			  and execution.status in (
			    com.greenhouse.backend.work.domain.WorkTargetExecutionStatus.PENDING,
			    com.greenhouse.backend.work.domain.WorkTargetExecutionStatus.IN_PROGRESS
			  )
			order by operation.plannedStartDate asc, operation.id asc
			""")
	List<WorkTargetExecution> findActiveInboundPottingForUpdate(
			@Param("inboundRecordId") Long inboundRecordId);

	@EntityGraph(attributePaths = "target")
	List<WorkTargetExecution> findByTargetWorkOperationIdOrderByIdAsc(Long workOperationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"target", "target.workOperation", "target.workOperation.workType"})
	List<WorkTargetExecution> findForUpdateByTargetWorkOperationIdOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = {"target", "target.workOperation"})
	Optional<WorkTargetExecution> findByTargetIdAndTargetWorkOperationId(Long targetId, Long workOperationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"target", "target.workOperation", "target.workOperation.workType"})
	Optional<WorkTargetExecution> findForUpdateByTargetIdAndTargetWorkOperationId(Long targetId, Long workOperationId);
}
