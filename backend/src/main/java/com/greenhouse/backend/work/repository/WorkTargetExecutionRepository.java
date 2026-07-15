package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkTargetExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTargetExecutionRepository extends JpaRepository<WorkTargetExecution, Long> {

	@EntityGraph(attributePaths = "target")
	List<WorkTargetExecution> findByTargetWorkOperationIdOrderByIdAsc(Long workOperationId);

	@EntityGraph(attributePaths = {"target", "target.workOperation"})
	Optional<WorkTargetExecution> findByTargetIdAndTargetWorkOperationId(Long targetId, Long workOperationId);
}
