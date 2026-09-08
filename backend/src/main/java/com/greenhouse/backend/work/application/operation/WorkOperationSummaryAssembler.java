package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.operation.WorkOperationProgress;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkOperationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkOperationProgressProjection;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorkOperationSummaryAssembler {

	private final WorkTargetExecutionRepository executionRepository;

	private final WorkOperationActionResolver actionResolver;

	List<WorkOperationSummaryResponse> assembleAll(List<WorkOperation> operations) {
		if (operations.isEmpty()) {
			return List.of();
		}
		Map<Long, WorkOperationProgressProjection> progressByOperationId = executionRepository
			.findProgressByWorkOperationIdIn(operations.stream().map(WorkOperation::getId).toList())
			.stream()
			.collect(Collectors.toMap(WorkOperationProgressProjection::workOperationId, Function.identity()));
		return operations.stream().map(operation -> {
			WorkOperationProgress progress = progress(progressByOperationId.get(operation.getId()));
			return WorkOperationSummaryResponse.from(operation, progress,
					actionResolver.resolveOperation(operation, progress));
		}).toList();
	}

	private WorkOperationProgress progress(WorkOperationProgressProjection projection) {
		if (projection == null) {
			return WorkOperationProgress.empty();
		}
		return WorkOperationProgress.fromCounts(projection.total(), projection.pending(), projection.inProgress(),
				projection.partial(), projection.completed(), projection.skipped(), projection.canceled(),
				projection.failed(), projection.totalQuantity(), projection.processedQuantity(),
				projection.skippedQuantity());
	}

}
