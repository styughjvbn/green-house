package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOperationRepository extends JpaRepository<WorkOperation, Long> {

	@EntityGraph(attributePaths = "workType")
	Optional<WorkOperation> findWithWorkTypeById(Long id);

	@EntityGraph(attributePaths = "workType")
	Optional<WorkOperation> findByRequestKey(String requestKey);
}
