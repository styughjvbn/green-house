package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.dto.orchid.OrchidGroupReconciliationRequest;
import com.greenhouse.backend.work.application.operation.ImmediateWorkExecutionService;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupReconciliationService {

	private final ImmediateWorkExecutionService immediateWorkExecutionService;

	public WorkOperationView reconcile(Long orchidGroupId, OrchidGroupReconciliationRequest request) {
		return immediateWorkExecutionService.executeForTarget(request.idempotencyKey(),
				WorkTypeDefinition.RECONCILIATION.name(), request.title(), request.workDate(), request.worker(),
				request.memo(), orchidGroupId, Map.of("reason", request.reason().trim()), request);
	}
}
