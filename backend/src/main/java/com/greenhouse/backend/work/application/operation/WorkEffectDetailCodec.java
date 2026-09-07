package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionAdjustmentResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionLocationResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionResultResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionSourceResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads persisted effect JSON, including legacy field precedence and numeric coercion. */
final class WorkEffectDetailCodec {

	static WorkExecutionDetailResponse execution(
			WorkAppliedEffect effect,
			List<WorkEffectOrchidGroup> links,
			Map<Long, String> resultVarietyNames,
			Map<Long, WorkExecutionLocationResponse> resultLocations) {
		Map<String, Object> command = map(effect.getCommandDetails());
		Map<String, Object> result = map(effect.getResultDetails());
		return new WorkExecutionDetailResponse(
				effect.getId(),
				effect.getEffectKey(),
				effect.getHandlerCode(),
				TimeConfig.toFarmTime(effect.getAppliedAt()),
				TimeConfig.toFarmTime(effect.getCanceledAt()),
				effect.getWorker(),
				effect.getTarget() == null ? null : effect.getTarget().getId(),
				longValue(result.get("inboundRecordId")),
				sources(command, result, links),
				results(command, result, links, resultVarietyNames, resultLocations),
				integerValue(result.get("lossQuantity")),
				integerValue(result.get("actualQuantity")),
				firstString(result.get("reason"), command.get("reason")),
				firstLong(result.get("discardWorkOperationId"), result.get("originalWorkOperationId")));
	}

	private static List<WorkExecutionSourceResponse> sources(
			Map<String, Object> command,
			Map<String, Object> result,
			List<WorkEffectOrchidGroup> links) {
		List<Map<String, Object>> adjustments = mapList(result.get("adjustments"));
		if (!adjustments.isEmpty()) {
			return adjustments.stream().map(row -> new WorkExecutionSourceResponse(
					longValue(row.get("orchidGroupId")), null,
					integerValue(row.get("beforeQuantity")), integerValue(row.get("afterQuantity")), null,
					stringValue(row.get("beforeStatus")), stringValue(row.get("afterStatus")),
					null, null, null)).toList();
		}

		if (result.containsKey("beforeQuantity")) {
			return List.of(new WorkExecutionSourceResponse(
					longValue(result.get("orchidGroupId")), integerValue(result.get("discardedQuantity")),
					integerValue(result.get("beforeQuantity")), integerValue(result.get("remainingQuantity")),
					integerValue(result.get("remainingQuantity")), stringValue(result.get("beforeStatus")),
					stringValue(result.get("status")), longValue(result.get("fromBedZoneId")), null, null));
		}

		List<Map<String, Object>> sourceRows = mapList(command.get("sources"));
		if (!sourceRows.isEmpty()) {
			return sourceRows.stream().map(row -> new WorkExecutionSourceResponse(
					longValue(row.get("sourceOrchidGroupId")), integerValue(row.get("inputQuantity")),
					null, null, null, null, null, null,
					decimalValue(row.get("releasedStartPosition")),
					decimalValue(row.get("releasedEndPosition")))).toList();
		}

		Map<String, Object> inputQuantities = map(result.get("sourceInputQuantities"));
		if (!inputQuantities.isEmpty()) {
			return inputQuantities.entrySet().stream().map(entry -> new WorkExecutionSourceResponse(
					longValue(entry.getKey()), integerValue(entry.getValue()), null, null, null,
					null, null, null, null, null)).toList();
		}

		Long directSourceId = longValue(result.get("sourceOrchidGroupId"));
		if (directSourceId == null) directSourceId = longValue(result.get("orchidGroupId"));
		if (directSourceId != null) {
			return List.of(new WorkExecutionSourceResponse(
					directSourceId, integerValue(result.get("inputQuantity")), null, null,
					integerValue(result.get("remainingQuantity")), null, null,
					longValue(result.get("fromBedZoneId")), null, null));
		}

		return links.stream()
				.filter(link -> link.getRelationType() == WorkEffectOrchidGroupRelationType.SOURCE)
				.map(link -> new WorkExecutionSourceResponse(
						link.getOrchidGroupId(), null, null, null, null, null, null, null, null, null))
				.toList();
	}

	private static List<WorkExecutionResultResponse> results(
			Map<String, Object> command,
			Map<String, Object> result,
			List<WorkEffectOrchidGroup> links,
			Map<Long, String> resultVarietyNames,
			Map<Long, WorkExecutionLocationResponse> resultLocations) {
		List<Map<String, Object>> commandRows = mapList(command.get("results"));
		List<Map<String, Object>> resultRows = mapList(result.get("results"));
		List<Long> resultIds = resultIds(result, links);
		int count = Math.max(commandRows.size(), Math.max(resultRows.size(), resultIds.size()));
		List<WorkExecutionResultResponse> rows = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			Map<String, Object> requested = index < commandRows.size() ? commandRows.get(index) : Map.of();
			Map<String, Object> applied = index < resultRows.size() ? resultRows.get(index) : Map.of();
			Long orchidGroupId = firstLong(
					applied.get("orchidGroupId"),
					index < resultIds.size() ? resultIds.get(index) : null);
			Long bedZoneId = firstLong(requested.get("bedZoneId"), result.get("toBedZoneId"));
			rows.add(new WorkExecutionResultResponse(
					orchidGroupId,
					firstInteger(applied.get("quantity"), requested.get("quantity")),
					firstString(applied.get("purpose"), requested.get("purpose")),
					bedZoneId,
					firstDecimal(requested.get("startPosition"), result.get("startPosition")),
					firstDecimal(requested.get("endPosition"), result.get("endPosition")),
					stringValue(requested.get("potSize")),
					integerValue(requested.get("ageYear")),
					stringValue(requested.get("placementType")),
					integerValue(requested.get("trayCount")),
					stringValue(requested.get("memo")),
					orchidGroupId == null ? null : resultVarietyNames.get(orchidGroupId),
					bedZoneId == null ? null : resultLocations.get(bedZoneId)));
		}

		if (rows.isEmpty() && result.containsKey("toBedZoneId")) {
			Long orchidGroupId = longValue(result.get("orchidGroupId"));
			Long bedZoneId = longValue(result.get("toBedZoneId"));
			rows.add(new WorkExecutionResultResponse(
					orchidGroupId, null, null,
					bedZoneId, decimalValue(result.get("startPosition")),
					decimalValue(result.get("endPosition")), null, null, null, null, null,
					orchidGroupId == null ? null : resultVarietyNames.get(orchidGroupId),
					bedZoneId == null ? null : resultLocations.get(bedZoneId)));
		}
		return rows;
	}

	static List<Long> resultIds(
			Map<String, Object> result,
			List<WorkEffectOrchidGroup> links) {
		List<Long> ids = longList(result.get("resultOrchidGroupIds"));
		if (ids.isEmpty()) ids = longList(result.get("createdOrchidGroupIds"));
		Long singleId = longValue(result.get("resultOrchidGroupId"));
		if (ids.isEmpty() && singleId != null) ids = List.of(singleId);
		if (!ids.isEmpty()) return ids;
		return links.stream()
				.filter(link -> link.getRelationType() != WorkEffectOrchidGroupRelationType.SOURCE)
				.map(WorkEffectOrchidGroup::getOrchidGroupId)
				.toList();
	}

	static Map<String, Object> map(Object value) {
		if (!(value instanceof Map<?, ?> source)) return Map.of();
		Map<String, Object> result = new LinkedHashMap<>();
		source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
		return result;
	}

	private static List<Map<String, Object>> mapList(Object value) {
		if (!(value instanceof List<?> list)) return List.of();
		return list.stream().map(WorkEffectDetailCodec::map).filter(row -> !row.isEmpty()).toList();
	}

	private static List<Long> longList(Object value) {
		if (!(value instanceof List<?> list)) return List.of();
		return list.stream().map(WorkEffectDetailCodec::longValue).filter(java.util.Objects::nonNull).toList();
	}

	private static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static String firstString(Object first, Object second) {
		String value = stringValue(first);
		return value != null ? value : stringValue(second);
	}

	private static Integer integerValue(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}

	private static Integer firstInteger(Object first, Object second) {
		Integer value = integerValue(first);
		return value != null ? value : integerValue(second);
	}

	private static Long longValue(Object value) {
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

	private static Long firstLong(Object first, Object second) {
		Long value = longValue(first);
		return value != null ? value : longValue(second);
	}

	private static BigDecimal decimalValue(Object value) {
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

	private static BigDecimal firstDecimal(Object first, Object second) {
		BigDecimal value = decimalValue(first);
		return value != null ? value : decimalValue(second);
	}

	static Set<Long> locationIds(WorkAppliedEffect effect) {
		Map<String, Object> command = map(effect.getCommandDetails());
		Map<String, Object> result = map(effect.getResultDetails());
		Set<Long> ids = mapList(command.get("results")).stream()
				.map(row -> longValue(row.get("bedZoneId"))).filter(java.util.Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Long directId = firstLong(command.get("toBedZoneId"), result.get("toBedZoneId"));
		if (directId != null) ids.add(directId);
		return ids;
	}

	static List<WorkCorrectionAdjustmentResponse> adjustments(Map<String, Object> result) {
		return mapList(result.get("adjustments")).stream().map(row -> new WorkCorrectionAdjustmentResponse(
				longValue(row.get("orchidGroupId")), integerValue(row.get("beforeQuantity")),
				integerValue(row.get("afterQuantity")), stringValue(row.get("beforeStatus")),
				stringValue(row.get("afterStatus")))).toList();
	}
}
