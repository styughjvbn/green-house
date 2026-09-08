package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.work.application.target.WorkOrchidGroupUsageInspector;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Translates Work's read contract into Farm's cancellation/correction blockers. */
@Component
@Order(300)
@RequiredArgsConstructor
public class WorkOrchidGroupUsageAdapter implements OrchidGroupUsageInspector {

	private final WorkOrchidGroupUsageInspector workUsage;

	@Override
	public List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId) {
		long count = workUsage.countOtherOperations(orchidGroupIds, sourceWorkOperationId);
		return count == 0 ? List.of()
				: List.of(new OrchidGroupUsage("WORK_OPERATION", "다른 작업에 포함된 난 묶음이 있습니다.", count));
	}

}
