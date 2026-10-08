package com.greenhouse.backend.work.api.effect;

import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Values available to effect handlers; Work retains its managed operation and target. */
public record WorkEffectContext(
    Long operationId, String workTypeCode, LocalDate plannedStartDate, String memo, Target target) {

  public record Target(
      WorkTargetReferenceType referenceType,
      Long orchidGroupId,
      Long inboundRecordId,
      Map<String, Object> locationSnapshot) {

    public Target {
      // Keep historical JSON values, including nulls, without sharing the entity's
      // mutable map.
      locationSnapshot =
          locationSnapshot == null
              ? null
              : Collections.unmodifiableMap(new LinkedHashMap<>(locationSnapshot));
    }
  }
}
