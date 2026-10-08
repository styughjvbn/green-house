package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.target.WorkOrchidGroupUsageApi;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class WorkOrchidGroupUsageInspector implements WorkOrchidGroupUsageApi {

  private final WorkOperationTargetRepository targetRepository;

  private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

  public WorkOrchidGroupUsageInspector(
      WorkOperationTargetRepository targetRepository,
      WorkEffectOrchidGroupRepository effectOrchidGroupRepository) {
    this.targetRepository = targetRepository;
    this.effectOrchidGroupRepository = effectOrchidGroupRepository;
  }

  @Override
  public boolean hasUncanceledReference(Long orchidGroupId) {
    var canceledStatuses = Set.of(WorkOperationStatus.CANCELED, WorkOperationStatus.VOIDED);
    return effectOrchidGroupRepository
            .existsByOrchidGroupIdAndWorkAppliedEffectWorkOperationStatusNotIn(
                orchidGroupId, canceledStatuses)
        || targetRepository.existsByOrchidGroupIdAndExcludedAtIsNullAndWorkOperationStatusNotIn(
            orchidGroupId, canceledStatuses);
  }

  @Override
  public long countOtherOperations(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
    var excludedIds =
        sourceWorkOperationId == null ? Set.<Long>of() : Set.of(sourceWorkOperationId);
    var canceledStatuses = Set.of(WorkOperationStatus.CANCELED, WorkOperationStatus.VOIDED);
    return targetRepository.countActiveOtherOperations(
            orchidGroupIds, sourceWorkOperationId, canceledStatuses)
        + effectOrchidGroupRepository.countOperationsOutside(
            orchidGroupIds, excludedIds, canceledStatuses);
  }

  @Override
  public boolean hasReferencesOutside(Set<Long> orchidGroupIds, Set<Long> workOperationIds) {
    var canceledStatuses = Set.of(WorkOperationStatus.CANCELED, WorkOperationStatus.VOIDED);
    return targetRepository.countOperationsOutside(
                orchidGroupIds, workOperationIds, canceledStatuses)
            > 0
        || effectOrchidGroupRepository.countOperationsOutside(
                orchidGroupIds, workOperationIds, canceledStatuses)
            > 0;
  }
}
