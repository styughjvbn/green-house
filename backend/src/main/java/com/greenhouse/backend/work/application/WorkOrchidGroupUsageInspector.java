package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.application.OrchidGroupUsage;
import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkOrchidGroupUsageInspector implements OrchidGroupUsageInspector {

	private final WorkOperationTargetRepository targetRepository;
	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

	public WorkOrchidGroupUsageInspector(
			WorkOperationTargetRepository targetRepository,
			WorkEffectOrchidGroupRepository effectOrchidGroupRepository) {
		this.targetRepository = targetRepository;
		this.effectOrchidGroupRepository = effectOrchidGroupRepository;
	}

	public boolean hasEffectReference(Long orchidGroupId) {
		return effectOrchidGroupRepository.existsByOrchidGroupId(orchidGroupId);
	}

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		List<OrchidGroupUsage> usages = new ArrayList<>();
		long operationCount = targetRepository
				.countByOrchidGroupIdInAndWorkOperationIdNot(orchidGroupIds, sourceWorkOperationId);
		if (operationCount > 0) {
			usages.add(new OrchidGroupUsage("WORK_OPERATION", "다른 작업에 포함된 난 묶음이 있습니다.", operationCount));
		}
		return usages;
	}
}
