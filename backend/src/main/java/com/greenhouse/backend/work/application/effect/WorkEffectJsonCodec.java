package com.greenhouse.backend.work.application.effect;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads saved effect facts without queries. Each consumer retains its legacy format semantics. */
public final class WorkEffectJsonCodec {

  private WorkEffectJsonCodec() {}

  /** Target responses expose the union of numeric IDs, in first-seen order. */
  public static List<Long> targetResultIds(Map<String, Object> details) {
    if (details == null || details.isEmpty()) return List.of();
    var ids = new LinkedHashSet<Long>();
    addNumericId(ids, details.get("resultOrchidGroupId"));
    addNumericIds(ids, details.get("resultOrchidGroupIds"));
    addNumericIds(ids, details.get("createdOrchidGroupIds"));
    if (details.get("results") instanceof List<?> rows) {
      for (Object value : rows) {
        if (value instanceof Map<?, ?> row) addNumericId(ids, row.get("orchidGroupId"));
      }
    }
    return new ArrayList<>(ids);
  }

  /** Detail responses prefer one stored ID list, accepting string IDs and retaining duplicates. */
  public static List<Long> detailResultIds(Map<String, Object> details) {
    List<Long> ids = longList(details.get("resultOrchidGroupIds"));
    if (ids.isEmpty()) ids = longList(details.get("createdOrchidGroupIds"));
    Long singleId = longValue(details.get("resultOrchidGroupId"));
    if (ids.isEmpty() && singleId != null) ids = List.of(singleId);
    return ids;
  }

  public static Map<Long, Integer> sourceQuantities(Map<String, Object> commandDetails) {
    return quantityMap(commandDetails.get("sources"), "sourceOrchidGroupId", "inputQuantity");
  }

  /** Numeric result rows take precedence over the legacy merge total, even if only partly valid. */
  public static Map<Long, Integer> resultQuantities(Map<String, Object> resultDetails) {
    Map<Long, Integer> quantities =
        quantityMap(resultDetails.get("results"), "orchidGroupId", "quantity");
    if (!quantities.isEmpty()) return quantities;
    Long resultId = numericId(resultDetails.get("resultOrchidGroupId"));
    Integer totalInput = integerValue(resultDetails.get("totalInputQuantity"));
    Integer loss = integerValue(resultDetails.get("lossQuantity"));
    if (resultId != null && totalInput != null) {
      quantities.put(resultId, totalInput - (loss == null ? 0 : loss));
    }
    return quantities;
  }

  /** Quantity correction snapshots reject malformed keys/amounts rather than losing saved facts. */
  public static Map<Long, Integer> inputQuantities(Object value) {
    if (!(value instanceof Map<?, ?> map)) return Map.of();
    var result = new LinkedHashMap<Long, Integer>();
    map.forEach(
        (key, quantity) ->
            result.put(Long.valueOf(key.toString()), ((Number) quantity).intValue()));
    return result;
  }

  /** Legacy potting associates IDs and request rows by position; incomplete/duplicate sets fail. */
  public static Map<Long, Integer> pottingResultQuantities(
      Object createdIds, Object requestedRows) {
    if (!(createdIds instanceof List<?> ids)
        || !(requestedRows instanceof List<?> rows)
        || ids.size() != rows.size()) return Map.of();
    var quantities = new LinkedHashMap<Long, Integer>();
    for (int i = 0; i < ids.size(); i++) {
      if (ids.get(i) instanceof Number id
          && rows.get(i) instanceof Map<?, ?> row
          && row.get("quantity") instanceof Number amount) {
        quantities.put(id.longValue(), amount.intValue());
      }
    }
    return quantities.size() == ids.size() ? quantities : Map.of();
  }

  public static Map<String, Object> map(Object value) {
    if (!(value instanceof Map<?, ?> source)) return Map.of();
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
    return result;
  }

  /** Detail rows historically compact empty/non-map entries before aligning request and result. */
  public static List<Map<String, Object>> mapList(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().map(WorkEffectJsonCodec::map).filter(row -> !row.isEmpty()).toList();
  }

  public static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  public static Integer integerValue(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  public static Long longValue(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value instanceof String text) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  public static BigDecimal decimalValue(Object value) {
    if (value instanceof BigDecimal decimal) return decimal;
    if (value instanceof Number number) return new BigDecimal(number.toString());
    if (value instanceof String text) {
      try {
        return new BigDecimal(text);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static List<Long> longList(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().map(WorkEffectJsonCodec::longValue).filter(Objects::nonNull).toList();
  }

  private static Map<Long, Integer> quantityMap(Object value, String idKey, String quantityKey) {
    Map<Long, Integer> quantities = new LinkedHashMap<>();
    if (!(value instanceof List<?> rows)) return quantities;
    for (Object rowValue : rows) {
      if (!(rowValue instanceof Map<?, ?> row)) continue;
      Long id = numericId(row.get(idKey));
      Integer quantity = integerValue(row.get(quantityKey));
      if (id != null && quantity != null) quantities.put(id, quantity);
    }
    return quantities;
  }

  private static Long numericId(Object value) {
    return value instanceof Number number ? number.longValue() : null;
  }

  private static void addNumericIds(LinkedHashSet<Long> ids, Object value) {
    if (value instanceof List<?> values) values.forEach(item -> addNumericId(ids, item));
  }

  private static void addNumericId(LinkedHashSet<Long> ids, Object value) {
    Long id = numericId(value);
    if (id != null) ids.add(id);
  }
}
