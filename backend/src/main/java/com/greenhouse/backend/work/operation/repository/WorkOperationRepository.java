package com.greenhouse.backend.work.operation.repository;

import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
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

public interface WorkOperationRepository
    extends JpaRepository<WorkOperation, Long>, WorkOperationRepositoryCustom {

  @Query(
      """
      select new com.greenhouse.backend.work.operation.repository.WorkOperationChildCount(
          o.parentOperation.id, count(o))
      from WorkOperation o
      where o.parentOperation.id in :parentIds and o.id <> o.parentOperation.id
      group by o.parentOperation.id
      """)
  List<WorkOperationChildCount> countChildrenByParentIds(Collection<Long> parentIds);

  @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from WorkOperation o where o.id = :id")
  Optional<WorkOperation> findForUpdateById(@Param("id") Long id);

  @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from WorkOperation o where o.id in :ids order by o.id")
  List<WorkOperation> findAllForUpdateByIdIn(@Param("ids") Collection<Long> ids);

  @EntityGraph(attributePaths = "workType")
  List<WorkOperation> findByIdIn(Collection<Long> ids);

  @EntityGraph(attributePaths = "workType")
  List<WorkOperation> findByParentOperationIdAndRelationTypeOrderByIdAsc(
      Long parentOperationId, WorkOperationRelationType relationType);

  @EntityGraph(attributePaths = "workType")
  List<WorkOperation> findByParentOperationIdAndRelationTypeOrderByIdAsc(
      Long parentOperationId, WorkOperationRelationType relationType, Pageable pageable);

  @EntityGraph(attributePaths = "workType")
  List<WorkOperation> findByParentOperationIdInOrderByParentOperationIdAscIdAsc(
      Collection<Long> parentOperationIds);

  @EntityGraph(attributePaths = "workType")
  @Query(
      value =
          """
			select o from WorkOperation o
			where exists (
				select t.id from WorkOperationTarget t
				where t.workOperation = o
				  and cast(sql('(? = any(?))', t.orchidGroupId, :orchidGroupIds) as boolean) = true
				  and t.excludedAt is null
			) or exists (
				select eg.id from WorkEffectOrchidGroup eg
				where eg.workAppliedEffect.workOperation = o
				  and cast(sql('(? = any(?))', eg.orchidGroupId, :orchidGroupIds) as boolean) = true
			)
			""",
      countQuery =
          """
			select count(o) from WorkOperation o
			where exists (
				select t.id from WorkOperationTarget t
				where t.workOperation = o
				  and cast(sql('(? = any(?))', t.orchidGroupId, :orchidGroupIds) as boolean) = true
				  and t.excludedAt is null
			) or exists (
				select eg.id from WorkEffectOrchidGroup eg
				where eg.workAppliedEffect.workOperation = o
				  and cast(sql('(? = any(?))', eg.orchidGroupId, :orchidGroupIds) as boolean) = true
			)
			""")
  Page<WorkOperation> findHistoryPageByIdArray(
      @Param("orchidGroupIds") Long[] orchidGroupIds, Pageable pageable);

  default Page<WorkOperation> findHistoryPage(Collection<Long> orchidGroupIds, Pageable pageable) {
    return findHistoryPageByIdArray(orchidGroupIds.toArray(Long[]::new), pageable);
  }
}
