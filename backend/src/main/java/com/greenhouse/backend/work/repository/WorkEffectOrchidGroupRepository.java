package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkEffectOrchidGroupRepository
    extends JpaRepository<WorkEffectOrchidGroup, Long> {

  @Query(
      "select distinct link.orchidGroupId from WorkEffectOrchidGroup link order by link.orchidGroupId")
  List<Long> findDistinctOrchidGroupIds();

  List<WorkEffectOrchidGroup> findByWorkAppliedEffectIdOrderByIdAsc(Long workAppliedEffectId);

  @EntityGraph(attributePaths = {"workAppliedEffect", "workAppliedEffect.workOperation"})
  List<WorkEffectOrchidGroup> findByWorkAppliedEffectIdInOrderByWorkAppliedEffectIdAscIdAsc(
      Collection<Long> workAppliedEffectIds);

  @EntityGraph(attributePaths = "workAppliedEffect")
  List<WorkEffectOrchidGroup> findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(
      Long workOperationId);

  @EntityGraph(attributePaths = "workAppliedEffect")
  List<WorkEffectOrchidGroup> findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(
      Long workOperationId, WorkEffectOrchidGroupRelationType relationType);

  @EntityGraph(
      attributePaths = {
        "workAppliedEffect",
        "workAppliedEffect.workOperation",
        "workAppliedEffect.workOperation.workType"
      })
  List<WorkEffectOrchidGroup>
      findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
          Long orchidGroupId);

  @EntityGraph(
      attributePaths = {
        "workAppliedEffect",
        "workAppliedEffect.workOperation",
        "workAppliedEffect.workOperation.workType"
      })
  @Query(
      "select link from WorkEffectOrchidGroup link where "
          + "cast(sql('(? = any(?))', link.orchidGroupId, :orchidGroupIds) as boolean) = true "
          + "order by link.workAppliedEffect.appliedAt desc, link.workAppliedEffect.id desc")
  List<WorkEffectOrchidGroup> findHistoryLinksByIdArray(Long[] orchidGroupIds);

  default List<WorkEffectOrchidGroup>
      findByOrchidGroupIdInOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(
          Collection<Long> orchidGroupIds) {
    return findHistoryLinksByIdArray(orchidGroupIds.toArray(Long[]::new));
  }

  @EntityGraph(
      attributePaths = {
        "workAppliedEffect",
        "workAppliedEffect.workOperation",
        "workAppliedEffect.workOperation.workType"
      })
  @Query(
      "select link from WorkEffectOrchidGroup link where link.workAppliedEffect.workOperation.id in :workOperationIds "
          + "and cast(sql('(? = any(?))', link.orchidGroupId, :orchidGroupIds) as boolean) = true "
          + "order by link.workAppliedEffect.workOperation.id, link.id")
  List<WorkEffectOrchidGroup> findPageHistoryLinksByIdArray(
      Collection<Long> workOperationIds, Long[] orchidGroupIds);

  default List<WorkEffectOrchidGroup>
      findByWorkAppliedEffectWorkOperationIdInAndOrchidGroupIdInOrderByWorkAppliedEffectWorkOperationIdAscIdAsc(
          Collection<Long> workOperationIds, Collection<Long> orchidGroupIds) {
    return findPageHistoryLinksByIdArray(workOperationIds, orchidGroupIds.toArray(Long[]::new));
  }

  boolean existsByOrchidGroupIdAndWorkAppliedEffectWorkOperationStatusNotIn(
      Long orchidGroupId, Collection<WorkOperationStatus> ignoredStatuses);

  @Query(
      """
			select count(distinct link.workAppliedEffect.workOperation.id) from WorkEffectOrchidGroup link
			where link.orchidGroupId in :orchidGroupIds
			  and link.workAppliedEffect.workOperation.id not in :workOperationIds
			  and link.workAppliedEffect.workOperation.status not in :ignoredStatuses
			""")
  long countOperationsOutside(
      Collection<Long> orchidGroupIds,
      Collection<Long> workOperationIds,
      Collection<WorkOperationStatus> ignoredStatuses);
}
