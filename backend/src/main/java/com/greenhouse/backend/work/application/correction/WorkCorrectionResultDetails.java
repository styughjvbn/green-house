package com.greenhouse.backend.work.application.correction;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionAdjustmentResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Shared reader for stored correction results, including legacy absent fields. */
public record WorkCorrectionResultDetails(
    LocalDate beforeWorkDate,
    LocalDate afterWorkDate,
    List<WorkCorrectionAdjustmentResponse> adjustments,
    List<WorkQuantityBalanceChange> quantityBalances) {
  private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

  public static WorkCorrectionResultDetails from(Map<String, Object> result) {
    var rows =
        MAPPER.convertValue(
            result.getOrDefault("adjustments", List.of()),
            WorkCorrectionAdjustmentResponse[].class);
    return new WorkCorrectionResultDetails(
        date(result.get("beforeWorkDate")),
        date(result.get("afterWorkDate")),
        List.of(rows),
        List.of(
            MAPPER.convertValue(
                result.getOrDefault("quantityBalances", List.of()),
                WorkQuantityBalanceChange[].class)));
  }

  private static LocalDate date(Object value) {
    return value == null ? null : MAPPER.convertValue(value, LocalDate.class);
  }
}
