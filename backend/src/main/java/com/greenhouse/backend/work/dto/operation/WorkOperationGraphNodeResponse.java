package com.greenhouse.backend.work.dto.operation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WorkOperationGraphNodeResponse(
    String id,
    WorkOperationGraphNodeType nodeType,
    boolean selected,
    WorkOperationOriginType originType,
    Long originReferenceId,
    Integer creationBatchSize,
    Long workOperationId,
    String workTypeCode,
    String workType,
    String title,
    String status,
    LocalDate workDate,
    List<Long> orchidGroupIds,
    List<String> varietyNames,
    Long mutationId,
    String mutationType,
    LocalDate effectiveBusinessDate,
    Instant occurredAt,
    Long orchidGroupId,
    Long stateRevision,
    WorkOperationGraphStateResponse state) {

  public static WorkOperationGraphNodeResponse origin(
      String id, WorkOperationOriginType originType, Long referenceId) {
    return new WorkOperationGraphNodeResponse(
        id,
        WorkOperationGraphNodeType.ORIGIN,
        false,
        originType,
        referenceId,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static WorkOperationGraphNodeResponse operation(
      String id,
      boolean selected,
      Long workOperationId,
      String workTypeCode,
      String workType,
      String title,
      String status,
      LocalDate workDate,
      List<Long> orchidGroupIds,
      List<String> varietyNames) {
    return new WorkOperationGraphNodeResponse(
        id,
        WorkOperationGraphNodeType.WORK_OPERATION,
        selected,
        null,
        null,
        null,
        workOperationId,
        workTypeCode,
        workType,
        title,
        status,
        workDate,
        orchidGroupIds,
        varietyNames,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static WorkOperationGraphNodeResponse mutation(
      String id,
      Long mutationId,
      String mutationType,
      LocalDate effectiveBusinessDate,
      Instant occurredAt) {
    return new WorkOperationGraphNodeResponse(
        id,
        WorkOperationGraphNodeType.MUTATION,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        mutationId,
        mutationType,
        effectiveBusinessDate,
        occurredAt,
        null,
        null,
        null);
  }

  public static WorkOperationGraphNodeResponse state(
      String id, Long orchidGroupId, Long stateRevision, WorkOperationGraphStateResponse state) {
    return new WorkOperationGraphNodeResponse(
        id,
        WorkOperationGraphNodeType.STATE,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(orchidGroupId),
        List.of(),
        null,
        null,
        null,
        null,
        orchidGroupId,
        stateRevision,
        state);
  }
}
