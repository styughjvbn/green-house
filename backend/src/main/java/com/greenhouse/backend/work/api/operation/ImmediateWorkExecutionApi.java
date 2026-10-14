package com.greenhouse.backend.work.api.operation;

import com.greenhouse.backend.work.api.effect.WorkEffectPayload;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Immediate Work history and its saved structure-change results used by Farm. */
public interface ImmediateWorkExecutionApi {

  WorkOperationView executeVarietyHistoryForTarget(
      String requestKey,
      String workTypeCode,
      String varietyName,
      LocalDate workDate,
      String worker,
      String memo,
      Long orchidGroupId,
      Map<String, Object> details,
      WorkEffectPayload payload);

  List<Long> getStructureChangeResultOrchidGroupIds(Long operationId, String workTypeCode);
}
