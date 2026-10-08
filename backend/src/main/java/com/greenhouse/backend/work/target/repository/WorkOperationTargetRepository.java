package com.greenhouse.backend.work.target.repository;

import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkOperationTargetRepository extends JpaRepository<WorkOperationTarget, Long> {

  default List<WorkOperationInboundReference> findInboundReferences(Collection<Long> operationIds) {
    return findInboundReferences(operationIds, Pageable.unpaged());
  }

  @Query(
      "select new com.greenhouse.backend.work.target.repository.WorkOperationInboundReference("
          + "t.workOperation.id, t.inboundRecordId) from WorkOperationTarget t "
          + "where t.workOperation.id in :operationIds and t.inboundRecordId is not null "
          + "group by t.workOperation.id, t.inboundRecordId order by t.workOperation.id, min(t.id)")
  List<WorkOperationInboundReference> findInboundReferences(
      Collection<Long> operationIds, Pageable pageable);

  @Query(
      "select distinct t.inboundRecordId from WorkOperationTarget t where t.workOperation.id in :operationIds "
          + "and t.inboundRecordId is not null order by t.inboundRecordId")
  List<Long> findInboundRecordIdsIn(Collection<Long> operationIds);

  @Query(
      "select distinct t.workOperation.id from WorkOperationTarget t where t.inboundRecordId in :inboundIds "
          + "and t.workOperation.workType.code = 'POTTING' and t.workOperation.status in :statuses "
          + "order by t.workOperation.id")
  List<Long> findActivePottingOperationIds(
      Collection<Long> inboundIds, Collection<WorkOperationStatus> statuses);

  boolean existsByOrchidGroupIdAndExcludedAtIsNullAndWorkOperationStatusNotIn(
      Long orchidGroupId, Collection<WorkOperationStatus> ignoredStatuses);

  @Query(
      "select distinct target.orchidGroupId from WorkOperationTarget target "
          + "where target.orchidGroupId is not null order by target.orchidGroupId")
  List<Long> findDistinctOrchidGroupIds();

  @Query(
      """
			select count(target) from WorkOperationTarget target
			where target.orchidGroupId in :orchidGroupIds
			  and (:workOperationId is null or target.workOperation.id <> :workOperationId)
			  and target.workOperation.status not in :ignoredStatuses
			  and target.excludedAt is null
			""")
  long countActiveOtherOperations(
      Collection<Long> orchidGroupIds,
      Long workOperationId,
      Collection<WorkOperationStatus> ignoredStatuses);

  @Query(
      """
			select count(distinct target.workOperation.id) from WorkOperationTarget target
			where target.orchidGroupId in :orchidGroupIds and target.excludedAt is null
			  and target.workOperation.id not in :workOperationIds
			  and target.workOperation.status not in :ignoredStatuses
			""")
  long countOperationsOutside(
      Collection<Long> orchidGroupIds,
      Collection<Long> workOperationIds,
      Collection<WorkOperationStatus> ignoredStatuses);

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  List<WorkOperationTarget> findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(
      Long workOperationId);

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  List<WorkOperationTarget>
      findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
          Collection<Long> workOperationIds);

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  List<WorkOperationTarget>
      findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
          Collection<Long> workOperationIds, Pageable pageable);

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  List<WorkOperationTarget> findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
      Collection<Long> workOperationIds);

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  List<WorkOperationTarget>
      findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
          Long orchidGroupId);

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  @Query(
      "select t from WorkOperationTarget t where "
          + "cast(sql('(? = any(?))', t.orchidGroupId, :orchidGroupIds) as boolean) = true "
          + "and t.excludedAt is null order by t.workOperation.plannedStartDate desc, t.workOperation.id desc")
  List<WorkOperationTarget> findHistoryTargetsByIdArray(Long[] orchidGroupIds);

  default List<WorkOperationTarget>
      findByOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
          Collection<Long> orchidGroupIds) {
    return findHistoryTargetsByIdArray(orchidGroupIds.toArray(Long[]::new));
  }

  @EntityGraph(attributePaths = {"workOperation", "workOperation.workType"})
  @Query(
      "select t from WorkOperationTarget t where t.workOperation.id in :workOperationIds "
          + "and cast(sql('(? = any(?))', t.orchidGroupId, :orchidGroupIds) as boolean) = true "
          + "and t.excludedAt is null order by t.workOperation.id, t.id")
  List<WorkOperationTarget> findPageHistoryTargetsByIdArray(
      Collection<Long> workOperationIds, Long[] orchidGroupIds);

  default List<WorkOperationTarget>
      findByWorkOperationIdInAndOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
          Collection<Long> workOperationIds, Collection<Long> orchidGroupIds) {
    return findPageHistoryTargetsByIdArray(workOperationIds, orchidGroupIds.toArray(Long[]::new));
  }
}
