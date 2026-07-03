package com.greenhouse.backend.auction.repository;

import com.greenhouse.backend.auction.domain.ImportRow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowRepository extends JpaRepository<ImportRow, Long> {
	List<ImportRow> findByImportBatchIdOrderByRowNumberAsc(Long importBatchId);
}
