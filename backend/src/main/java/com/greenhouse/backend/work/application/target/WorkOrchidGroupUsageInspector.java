package com.greenhouse.backend.work.application.target;

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

	public boolean hasEffectReference(Long orchidGroupId) {
		return effectOrchidGroupRepository.existsByOrchidGroupId(orchidGroupId);
	}

	public long countOtherOperations(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		return targetRepository.countByOrchidGroupIdInAndWorkOperationIdNot(orchidGroupIds, sourceWorkOperationId);
	}

}
