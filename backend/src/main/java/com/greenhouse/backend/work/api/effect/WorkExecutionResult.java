package com.greenhouse.backend.work.api.effect;

import java.util.List;
import java.util.Map;

public record WorkExecutionResult(
    String handlerCode,
    WorkEffectResultDetails details,
    List<Long> resultOrchidGroupIds,
    WorkMutationLink mutationLink) {

  public WorkExecutionResult(
      String handlerCode, WorkEffectResultDetails details, List<Long> resultOrchidGroupIds) {
    this(handlerCode, details, resultOrchidGroupIds, null);
  }

  public Map<String, Object> storedDetails() {
    return details.toMap();
  }

  public static WorkExecutionResult fromStored(
      String handlerCode,
      Map<String, Object> storedDetails,
      List<Long> resultOrchidGroupIds,
      WorkMutationLink mutationLink) {
    return new WorkExecutionResult(
        handlerCode, new WorkEffectResults.Json(storedDetails), resultOrchidGroupIds, mutationLink);
  }
}
