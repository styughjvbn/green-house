package com.greenhouse.backend.work.dto.operation;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WorkCorrectionDetailResponse(Long id, LocalDateTime createdAt, String worker, String memo, String reason,
		LocalDate beforeWorkDate, LocalDate afterWorkDate, List<WorkCorrectionAdjustmentResponse> adjustments) {

	private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

	public static WorkCorrectionDetailResponse from(WorkOperationCorrection correction) {
		var result = correction.getResultDetails();
		var rows = MAPPER.convertValue(result.getOrDefault("adjustments", List.of()),
				WorkCorrectionAdjustmentResponse[].class);
		return new WorkCorrectionDetailResponse(correction.getId(), TimeConfig.toFarmTime(correction.getCreatedAt()),
				correction.getWorker(), correction.getMemo(), correction.getReason(),
				date(result.get("beforeWorkDate")), date(result.get("afterWorkDate")), List.of(rows));
	}

	private static LocalDate date(Object value) {
		if (value == null)
			return null;
		return MAPPER.convertValue(value, LocalDate.class);
	}
}
