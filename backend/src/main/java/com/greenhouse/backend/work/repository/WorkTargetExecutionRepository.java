package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkTargetExecutionRepository
		extends JpaRepository<WorkTargetExecution, Long>, WorkTargetExecutionRepositoryCustom {

	@Query("""
			select new com.greenhouse.backend.work.repository.WorkExecutionReconciliationRow(
				execution.id,
				target.orchidGroupId,
				target.quantitySnapshot,
				execution.processedQuantity,
				execution.status,
				execution.effectAppliedAt)
			from WorkTargetExecution execution
			join execution.target target
			where target.orchidGroupId is not null
			order by execution.id
			""")
	List<WorkExecutionReconciliationRow> findReconciliationRows();

}
