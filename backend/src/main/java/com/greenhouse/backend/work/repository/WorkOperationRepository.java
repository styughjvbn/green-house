package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOperationRepository
		extends JpaRepository<WorkOperation, Long>, WorkOperationRepositoryCustom {

	@EntityGraph(attributePaths = "workType")
	@Query(
			value = """
					select o from WorkOperation o
					where exists (
						select t.id from WorkOperationTarget t
						where t.workOperation = o
						  and t.orchidGroupId in :orchidGroupIds
						  and t.excludedAt is null
					) or exists (
						select eg.id from WorkEffectOrchidGroup eg
						where eg.workAppliedEffect.workOperation = o
						  and eg.orchidGroupId in :orchidGroupIds
					)
					""",
			countQuery = """
					select count(o) from WorkOperation o
					where exists (
						select t.id from WorkOperationTarget t
						where t.workOperation = o
						  and t.orchidGroupId in :orchidGroupIds
						  and t.excludedAt is null
					) or exists (
						select eg.id from WorkEffectOrchidGroup eg
						where eg.workAppliedEffect.workOperation = o
						  and eg.orchidGroupId in :orchidGroupIds
					)
					""")
	Page<WorkOperation> findHistoryPage(
			@Param("orchidGroupIds") Collection<Long> orchidGroupIds,
			Pageable pageable);
}
