package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkOperationRepository extends JpaRepository<WorkOperation, Long>, WorkOperationRepositoryCustom {

	@org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from WorkOperation o where o.id = :id")
	Optional<WorkOperation> findForUpdateById(@Param("id") Long id);

	@org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from WorkOperation o where o.id in :ids order by o.id")
	List<WorkOperation> findAllForUpdateByIdIn(@Param("ids") Collection<Long> ids);

	@EntityGraph(attributePaths = "workType")
	List<WorkOperation> findByIdIn(Collection<Long> ids);

	@EntityGraph(attributePaths = "workType")
	List<WorkOperation> findByParentOperationIdAndRelationTypeOrderByIdAsc(Long parentOperationId,
			WorkOperationRelationType relationType);

	@EntityGraph(attributePaths = "workType")
	List<WorkOperation> findByParentOperationIdInOrderByParentOperationIdAscIdAsc(Collection<Long> parentOperationIds);

	@EntityGraph(attributePaths = "workType")
	@Query(value = """
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
			""", countQuery = """
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
	Page<WorkOperation> findHistoryPage(@Param("orchidGroupIds") Collection<Long> orchidGroupIds, Pageable pageable);

}
