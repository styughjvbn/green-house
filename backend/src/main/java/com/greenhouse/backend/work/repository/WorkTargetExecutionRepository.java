package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkTargetExecution;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkTargetExecutionRepository extends JpaRepository<WorkTargetExecution, Long> {

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
