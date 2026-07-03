package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.ImportBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
	@EntityGraph(attributePaths = "rows")
	Optional<ImportBatch> findWithRowsById(Long id);
}
