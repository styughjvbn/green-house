package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Legacy v1 receipt hash input. Free-form details remain part of the complete request. */
record WorkCreationRequestPayload(
    Long workTypeId,
    String title,
    LocalDate plannedStartDate,
    LocalDate plannedEndDate,
    WorkSourceScopeType sourceScopeType,
    Long sourceScopeId,
    String sourceDerivedGroupKey,
    List<Long> sourceOrchidGroupIds,
    Map<String, Object> details,
    String worker,
    String memo,
    List<Long> excludedOrchidGroupIds) {

  static WorkCreationRequestPayload from(WorkOperationCreateRequest request) {
    return request == null
        ? null
        : new WorkCreationRequestPayload(
            request.workTypeId(),
            request.title(),
            request.plannedStartDate(),
            request.plannedEndDate(),
            request.sourceScopeType(),
            request.sourceScopeId(),
            request.sourceDerivedGroupKey(),
            request.sourceOrchidGroupIds(),
            request.details(),
            request.worker(),
            request.memo(),
            request.excludedOrchidGroupIds());
  }

  static Batch from(WorkOperationBatchCreateRequest request) {
    return request == null ? null : new Batch(from(request.operation()));
  }

  record Batch(WorkCreationRequestPayload operation) {}
}
