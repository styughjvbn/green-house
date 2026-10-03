package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkOperationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
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

  private final WorkOperationCorrectionRepository correctionRepository;

  private final WorkOperationRelationSummaryAssembler relationSummaryAssembler;

  List<WorkOperationSummaryResponse> assembleAll(List<WorkOperation> operations) {
    if (operations.isEmpty()) {
      return List.of();
    }
    Map<Long, WorkOperationProgressProjection> progressByOperationId =
        executionRepository
            .findProgressByWorkOperationIdIn(operations.stream().map(WorkOperation::getId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    WorkOperationProgressProjection::workOperationId, Function.identity()));
    var relationSummaries = relationSummaryAssembler.assemble(operations);
    var correctionCounts =
        correctionRepository
            .countByOriginalIds(operations.stream().map(WorkOperation::getId).toList())
            .stream()
            .collect(Collectors.toMap(row -> row.getOperationId(), row -> row.getTotal()));
    return operations.stream()
        .map(
            operation -> {
              WorkOperationProgress progress =
                  progress(progressByOperationId.get(operation.getId()))
                      .forStatus(operation.getStatus());
              return WorkOperationSummaryResponse.from(
                  operation,
                  relationSummaries.get(operation.getId()),
                  progress,
                  actionResolver.resolveOperation(operation, progress),
                  correctionCounts.getOrDefault(operation.getId(), 0L));
            })
        .toList();
  }

  private WorkOperationProgress progress(WorkOperationProgressProjection projection) {
    if (projection == null) {
      return WorkOperationProgress.empty();
    }
    return WorkOperationProgress.fromCounts(
        projection.total(),
        projection.pending(),
        projection.inProgress(),
        projection.partial(),
        projection.completed(),
        projection.skipped(),
        projection.canceled(),
        projection.failed(),
        projection.totalQuantity(),
        projection.processedQuantity(),
        projection.skippedQuantity());
  }
}
