package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkOperationCorrection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOperationCorrectionRepository extends JpaRepository<WorkOperationCorrection, Long> {

	@EntityGraph(attributePaths = {"originalWorkOperation", "correctionWorkOperation"})
	List<WorkOperationCorrection> findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(Long originalWorkOperationId);

	Optional<WorkOperationCorrection> findByCorrectionWorkOperationId(Long correctionWorkOperationId);
}
