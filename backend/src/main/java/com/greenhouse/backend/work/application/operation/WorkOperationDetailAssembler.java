package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationDetailFieldResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class WorkOperationDetailAssembler {

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

	static List<WorkOperationDetailFieldResponse> fields(WorkOperation operation) {
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

	private static boolean isHiddenFieldKey(String key) {
		return HIDDEN_FIELD_KEYS.contains(key)
				|| key.equals("migrationSource")
				|| key.startsWith("legacy");
	}

	private static String fieldLabel(WorkOperation operation, String key) {
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

	private static boolean isDisplayValue(Object value) {
		return value != null
				&& (!(value instanceof String text) || !text.isBlank())
				&& !(value instanceof Map<?, ?>)
				&& (!(value instanceof List<?> list)
						|| list.stream().noneMatch(item -> item instanceof Map<?, ?> || item instanceof List<?>));
	}

	private static String formatValue(Object value) {
		if (value instanceof Boolean flag) return flag ? "예" : "아니오";
		if (value instanceof List<?> list) {
			return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
		}
		return String.valueOf(value);
	}

	static WorkCorrectionDetailResponse correction(WorkOperationCorrection correction, Map<String, Object> result) {
		WorkOperation operation = correction.getCorrectionWorkOperation();
		return new WorkCorrectionDetailResponse(
				correction.getId(), operation.getId(), operation.getTitle(), operation.getPlannedStartDate(),
				TimeConfig.toFarmTime(correction.getCreatedAt()), operation.getWorker(), correction.getReason(),
				WorkEffectDetailCodec.adjustments(result));
	}
}
