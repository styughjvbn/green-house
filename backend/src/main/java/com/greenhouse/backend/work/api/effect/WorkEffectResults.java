package com.greenhouse.backend.work.api.effect;

import com.greenhouse.backend.work.api.correction.WorkQuantityBalanceChange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed execution results. JSON field presence remains part of the persisted contract. */
public final class WorkEffectResults {

  private WorkEffectResults() {}

  public record ResultGroup(
      Long orchidGroupId, int quantity, StructureChangeResultPurpose purpose) {
    Map<String, Object> toMap() {
      return Map.of(
          "orchidGroupId", orchidGroupId, "quantity", quantity, "purpose", purpose.name());
    }
  }

  public record Transformation(
      String executionKey,
      Map<Long, Integer> sourceInputQuantities,
      int lossQuantity,
      int increaseQuantity,
      List<ResultGroup> results,
      Integer remainingQuantity,
      boolean identityPreserved)
      implements WorkEffectResultDetails {
    public Transformation(
        String executionKey,
        Map<Long, Integer> sourceInputQuantities,
        int lossQuantity,
        int increaseQuantity,
        List<ResultGroup> results,
        Integer remainingQuantity) {
      this(
          executionKey,
          sourceInputQuantities,
          lossQuantity,
          increaseQuantity,
          results,
          remainingQuantity,
          false);
    }

    public Map<String, Object> toMap() {
      var json = new LinkedHashMap<String, Object>();
      json.put("executionKey", executionKey);
      json.put("sourceInputQuantities", sourceInputQuantities);
      json.put("lossQuantity", lossQuantity);
      json.put("increaseQuantity", increaseQuantity);
      json.put("results", results.stream().map(ResultGroup::toMap).toList());
      if (sourceInputQuantities.size() == 1) {
        Long sourceId = sourceInputQuantities.keySet().iterator().next();
        json.put("sourceOrchidGroupId", sourceId);
        json.put("inputQuantity", sourceInputQuantities.get(sourceId));
        json.put("remainingQuantity", remainingQuantity);
        json.put("resultOrchidGroupIds", results.stream().map(ResultGroup::orchidGroupId).toList());
      }
      if (identityPreserved) json.put("identityPreserved", true);
      return json;
    }
  }

  public record Merged(
      List<Long> sourceOrchidGroupIds,
      Map<Long, Integer> sourceInputQuantities,
      int totalInputQuantity,
      int lossQuantity,
      Long resultOrchidGroupId)
      implements WorkEffectResultDetails {
    public Map<String, Object> toMap() {
      return Map.of(
          "sourceOrchidGroupIds",
          sourceOrchidGroupIds,
          "sourceInputQuantities",
          sourceInputQuantities,
          "totalInputQuantity",
          totalInputQuantity,
          "lossQuantity",
          lossQuantity,
          "resultOrchidGroupId",
          resultOrchidGroupId);
    }
  }

  public record Created(List<Long> createdOrchidGroupIds) implements WorkEffectResultDetails {
    public Map<String, Object> toMap() {
      return Map.of(
          "createdCount",
          createdOrchidGroupIds.size(),
          "createdOrchidGroupIds",
          createdOrchidGroupIds);
    }
  }

  public record Potted(Long inboundRecordId, List<Long> createdOrchidGroupIds, int actualQuantity)
      implements WorkEffectResultDetails {
    public Map<String, Object> toMap() {
      return Map.of(
          "inboundRecordId",
          inboundRecordId,
          "createdOrchidGroupIds",
          createdOrchidGroupIds,
          "actualQuantity",
          actualQuantity,
          "resultCount",
          createdOrchidGroupIds.size());
    }
  }

  // Preserve the raw stored source-location value; legacy snapshots may contain a
  // string ID.
  public record Moved(
      Long orchidGroupId,
      Object fromBedZoneId,
      Long toBedZoneId,
      BigDecimal startPosition,
      BigDecimal endPosition)
      implements WorkEffectResultDetails {
    public Map<String, Object> toMap() {
      var json = new LinkedHashMap<String, Object>();
      json.put("orchidGroupId", orchidGroupId);
      json.put("fromBedZoneId", fromBedZoneId);
      json.put("toBedZoneId", toBedZoneId);
      json.put("startPosition", startPosition);
      json.put("endPosition", endPosition);
      return json;
    }
  }

  public record Discarded(
      Long orchidGroupId,
      int beforeQuantity,
      int discardedQuantity,
      int remainingQuantity,
      String beforeStatus,
      String status,
      String reason)
      implements WorkEffectResultDetails {
    public Map<String, Object> toMap() {
      var json = new LinkedHashMap<String, Object>();
      json.put("orchidGroupId", orchidGroupId);
      json.put("beforeQuantity", beforeQuantity);
      json.put("discardedQuantity", discardedQuantity);
      json.put("remainingQuantity", remainingQuantity);
      json.put("beforeStatus", beforeStatus);
      json.put("status", status);
      if (reason != null && !reason.isBlank()) json.put("reason", reason.trim());
      return json;
    }
  }

  public record Adjustment(
      Long orchidGroupId,
      int beforeQuantity,
      String beforeStatus,
      int afterQuantity,
      String afterStatus) {
    Map<String, Object> toMap() {
      return Map.of(
          "orchidGroupId",
          orchidGroupId,
          "beforeQuantity",
          beforeQuantity,
          "beforeStatus",
          beforeStatus,
          "afterQuantity",
          afterQuantity,
          "afterStatus",
          afterStatus);
    }
  }

  public record Corrected(
      Long originalWorkOperationId,
      LocalDate beforeWorkDate,
      LocalDate afterWorkDate,
      List<Adjustment> adjustments,
      List<WorkQuantityBalanceChange> quantityBalances)
      implements WorkEffectResultDetails {
    public Corrected(
        Long originalWorkOperationId,
        LocalDate beforeWorkDate,
        LocalDate afterWorkDate,
        List<Adjustment> adjustments) {
      this(originalWorkOperationId, beforeWorkDate, afterWorkDate, adjustments, List.of());
    }

    public Map<String, Object> toMap() {
      var json = new LinkedHashMap<String, Object>();
      json.put("originalWorkOperationId", originalWorkOperationId);
      json.put("beforeWorkDate", beforeWorkDate);
      json.put("afterWorkDate", afterWorkDate);
      json.put("adjustments", adjustments.stream().map(Adjustment::toMap).toList());
      if (!quantityBalances.isEmpty()) json.put("quantityBalances", quantityBalances);
      return json;
    }
  }

  /** Free-form records and replayed JSON retain unknown fields without reconstruction. */
  public record Json(Map<String, Object> value) implements WorkEffectResultDetails {
    public Map<String, Object> toMap() {
      return value;
    }
  }
}
