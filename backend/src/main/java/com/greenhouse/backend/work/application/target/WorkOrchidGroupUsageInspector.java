package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class WorkOrchidGroupUsageInspector {

	private final WorkOperationTargetRepository targetRepository;

	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

	public WorkOrchidGroupUsageInspector(WorkOperationTargetRepository targetRepository,
			WorkEffectOrchidGroupRepository effectOrchidGroupRepository) {
		this.targetRepository = targetRepository;
		this.effectOrchidGroupRepository = effectOrchidGroupRepository;
	}

	public boolean hasUncanceledReference(Long orchidGroupId) {
		var canceledStatuses = Set.of(WorkOperationStatus.CANCELED, WorkOperationStatus.VOIDED);
		return effectOrchidGroupRepository
			.existsByOrchidGroupIdAndWorkAppliedEffectWorkOperationStatusNotIn(orchidGroupId, canceledStatuses)
				|| targetRepository.existsByOrchidGroupIdAndExcludedAtIsNullAndWorkOperationStatusNotIn(orchidGroupId,
						canceledStatuses);
	}

	public long countOtherOperations(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		var excludedIds = sourceWorkOperationId == null ? Set.<Long>of() : Set.of(sourceWorkOperationId);
		var canceledStatuses = Set.of(WorkOperationStatus.CANCELED, WorkOperationStatus.VOIDED);
		return targetRepository.countActiveOtherOperations(orchidGroupIds, sourceWorkOperationId, canceledStatuses)
				+ effectOrchidGroupRepository.countOperationsOutside(orchidGroupIds, excludedIds, canceledStatuses);
	}

	public boolean hasReferencesOutside(Set<Long> orchidGroupIds, Set<Long> workOperationIds) {
		var canceledStatuses = Set.of(WorkOperationStatus.CANCELED, WorkOperationStatus.VOIDED);
		return targetRepository.countOperationsOutside(orchidGroupIds, workOperationIds, canceledStatuses) > 0
				|| effectOrchidGroupRepository.countOperationsOutside(orchidGroupIds, workOperationIds,
						canceledStatuses) > 0;
	}

}
