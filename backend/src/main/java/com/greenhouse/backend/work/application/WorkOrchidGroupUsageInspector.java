package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.application.OrchidGroupUsage;
import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkOrchidGroupUsageInspector implements OrchidGroupUsageInspector {

	private final WorkOperationTargetRepository targetRepository;
	private final WorkRecordRepository workRecordRepository;

	public WorkOrchidGroupUsageInspector(
			WorkOperationTargetRepository targetRepository,
			WorkRecordRepository workRecordRepository) {
		this.targetRepository = targetRepository;
		this.workRecordRepository = workRecordRepository;
	}

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		List<OrchidGroupUsage> usages = new ArrayList<>();
		long operationCount = targetRepository
				.countByOrchidGroupIdInAndWorkOperationIdNot(orchidGroupIds, sourceWorkOperationId);
		if (operationCount > 0) {
			usages.add(new OrchidGroupUsage("WORK_OPERATION", "다른 작업에 포함된 난 묶음이 있습니다.", operationCount));
		}
		long recordCount = workRecordRepository.countByTargetTypeAndTargetIdIn("ORCHID_GROUP", orchidGroupIds);
		if (recordCount > 0) {
			usages.add(new OrchidGroupUsage("WORK_RECORD", "작업 또는 이동 이력이 있는 난 묶음이 있습니다.", recordCount));
		}
		return usages;
	}
}
