package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionAdjustmentResponse;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionResultResponse;
import com.greenhouse.backend.work.dto.operation.WorkExecutionSourceResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailFieldResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailSummaryResponse;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationDetailService {

	private static final Set<String> HIDDEN_FIELD_KEYS = Set.of(
			"inboundRecordId", "orchidGroupId", "requestKey", "idempotencyKey");
	private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
			Map.entry("actualQuantity", "실제 수량"),
			Map.entry("ageYear", "년생"),
			Map.entry("bottleCount", "병 수"),
			Map.entry("createdCount", "생성된 난 묶음 수"),
			Map.entry("dilutionRatio", "희석 배수"),
			Map.entry("estimatedQuantity", "예상 수량"),
			Map.entry("genus", "속명"),
			Map.entry("growthStage", "생육 단계"),
			Map.entry("inboundType", "입고 유형"),
			Map.entry("materialName", "자재명"),
			Map.entry("originalWorkOperationId", "원본 작업"),
			Map.entry("placementType", "배치 규격"),
			Map.entry("potSize", "화분 크기"),
			Map.entry("pottingDueDate", "포트 예정일"),
			Map.entry("quantity", "사용량"),
			Map.entry("reason", "보정 사유"),
			Map.entry("resultCount", "결과 난 묶음 수"),
			Map.entry("rowCount", "생성 예정 건수"),
			Map.entry("status", "상태"),
			Map.entry("tempLocation", "임시 위치"),
			Map.entry("trayCount", "판수"),
			Map.entry("varietyName", "품종"));

	private final WorkOperationRepository operationRepository;
	private final WorkAppliedEffectRepository effectRepository;
	private final WorkEffectOrchidGroupRepository effectGroupRepository;
	private final WorkOperationCorrectionRepository correctionRepository;

	public WorkOperationDetailResponse get(Long operationId) {
		WorkOperation operation = operationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		List<WorkAppliedEffect> effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
		Map<Long, List<WorkEffectOrchidGroup>> linksByEffectId = effectGroupRepository
				.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId).stream()
				.collect(Collectors.groupingBy(
						link -> link.getWorkAppliedEffect().getId(),
						LinkedHashMap::new,
						Collectors.toList()));
		return new WorkOperationDetailResponse(
				WorkOperationDetailSummaryResponse.from(operation),
				fields(operation),
				effects.stream()
						.map(effect -> execution(effect, linksByEffectId.getOrDefault(effect.getId(), List.of())))
						.toList(),
				corrections(operationId));
	}

	private List<WorkOperationDetailFieldResponse> fields(WorkOperation operation) {
		if (operation.getDetails() == null) return List.of();
		return operation.getDetails().entrySet().stream()
				.filter(entry -> !isHiddenFieldKey(entry.getKey()))
				.filter(entry -> isDisplayValue(entry.getValue()))
				.map(entry -> new WorkOperationDetailFieldResponse(
						entry.getKey(),
						fieldLabel(operation, entry.getKey()),
						formatValue(entry.getValue())))
				.toList();
	}

	private boolean isHiddenFieldKey(String key) {
		return HIDDEN_FIELD_KEYS.contains(key)
				|| key.equals("migrationSource")
				|| key.startsWith("legacy");
	}

	private String fieldLabel(WorkOperation operation, String key) {
		return switch (key) {
			case "materialName" -> switch (operation.getWorkType().getTemplate()) {
				case PESTICIDE -> "약제명";
				case FERTILIZER -> "비료/자재명";
				default -> FIELD_LABELS.getOrDefault(key, key);
			};
			case "quantity" -> switch (operation.getWorkType().getTemplate()) {
				case PESTICIDE, FERTILIZER -> "사용량";
				case REPOT, CLEANUP -> "작업 수량";
				case DISCARD -> "폐기 수량";
				default -> FIELD_LABELS.getOrDefault(key, key);
			};
			default -> FIELD_LABELS.getOrDefault(key, key);
		};
	}

	private WorkExecutionDetailResponse execution(
			WorkAppliedEffect effect,
			List<WorkEffectOrchidGroup> links) {
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
				results(command, result, links),
				integerValue(result.get("lossQuantity")),
				integerValue(result.get("actualQuantity")),
				firstString(result.get("reason"), command.get("reason")),
				firstLong(result.get("discardWorkOperationId"), result.get("originalWorkOperationId")));
	}

	private List<WorkExecutionSourceResponse> sources(
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

	private List<WorkExecutionResultResponse> results(
			Map<String, Object> command,
			Map<String, Object> result,
			List<WorkEffectOrchidGroup> links) {
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
			rows.add(new WorkExecutionResultResponse(
					orchidGroupId,
					firstInteger(applied.get("quantity"), requested.get("quantity")),
					firstString(applied.get("purpose"), requested.get("purpose")),
					firstLong(requested.get("bedZoneId"), result.get("toBedZoneId")),
					firstDecimal(requested.get("startPosition"), result.get("startPosition")),
					firstDecimal(requested.get("endPosition"), result.get("endPosition")),
					stringValue(requested.get("potSize")),
					integerValue(requested.get("ageYear")),
					stringValue(requested.get("placementType")),
					integerValue(requested.get("trayCount")),
					stringValue(requested.get("memo"))));
		}

		if (rows.isEmpty() && result.containsKey("toBedZoneId")) {
			rows.add(new WorkExecutionResultResponse(
					longValue(result.get("orchidGroupId")), null, null,
					longValue(result.get("toBedZoneId")), decimalValue(result.get("startPosition")),
					decimalValue(result.get("endPosition")), null, null, null, null, null));
		}
		return rows;
	}

	private List<Long> resultIds(
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

	private List<WorkCorrectionDetailResponse> corrections(Long operationId) {
		return correctionRepository.findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(operationId).stream()
				.map(correction -> {
					WorkOperation correctionOperation = correction.getCorrectionWorkOperation();
					Map<String, Object> result = effectRepository
							.findByWorkOperationIdAndEffectKey(correctionOperation.getId(), "OPERATION")
							.map(WorkAppliedEffect::getResultDetails)
							.orElse(Map.of());
					List<WorkCorrectionAdjustmentResponse> adjustments = mapList(result.get("adjustments"))
							.stream()
							.map(row -> new WorkCorrectionAdjustmentResponse(
									longValue(row.get("orchidGroupId")),
									integerValue(row.get("beforeQuantity")),
									integerValue(row.get("afterQuantity")),
									stringValue(row.get("beforeStatus")),
									stringValue(row.get("afterStatus"))))
							.toList();
					return new WorkCorrectionDetailResponse(
							correction.getId(), correctionOperation.getId(), correctionOperation.getTitle(),
							correctionOperation.getPlannedStartDate(), TimeConfig.toFarmTime(correction.getCreatedAt()),
							correctionOperation.getWorker(), correction.getReason(), adjustments);
				})
				.toList();
	}

	private boolean isDisplayValue(Object value) {
		return value != null
				&& (!(value instanceof String text) || !text.isBlank())
				&& !(value instanceof Map<?, ?>)
				&& (!(value instanceof List<?> list)
						|| list.stream().noneMatch(item -> item instanceof Map<?, ?> || item instanceof List<?>));
	}

	private String formatValue(Object value) {
		if (value instanceof Boolean flag) return flag ? "예" : "아니오";
		if (value instanceof List<?> list) {
			return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
		}
		return String.valueOf(value);
	}

	private Map<String, Object> map(Object value) {
		if (!(value instanceof Map<?, ?> source)) return Map.of();
		Map<String, Object> result = new LinkedHashMap<>();
		source.forEach((key, nested) -> result.put(String.valueOf(key), nested));
		return result;
	}

	private List<Map<String, Object>> mapList(Object value) {
		if (!(value instanceof List<?> list)) return List.of();
		return list.stream().map(this::map).filter(row -> !row.isEmpty()).toList();
	}

	private List<Long> longList(Object value) {
		if (!(value instanceof List<?> list)) return List.of();
		return list.stream().map(this::longValue).filter(java.util.Objects::nonNull).toList();
	}

	private String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private String firstString(Object first, Object second) {
		String value = stringValue(first);
		return value != null ? value : stringValue(second);
	}

	private Integer integerValue(Object value) {
		return value instanceof Number number ? number.intValue() : null;
	}

	private Integer firstInteger(Object first, Object second) {
		Integer value = integerValue(first);
		return value != null ? value : integerValue(second);
	}

	private Long longValue(Object value) {
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

	private Long firstLong(Object first, Object second) {
		Long value = longValue(first);
		return value != null ? value : longValue(second);
	}

	private BigDecimal decimalValue(Object value) {
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

	private BigDecimal firstDecimal(Object first, Object second) {
		BigDecimal value = decimalValue(first);
		return value != null ? value : decimalValue(second);
	}
}
