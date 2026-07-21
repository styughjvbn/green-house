package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkRecordStatus;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record WorkRecordResponse(
		Long id,
		Long workTypeId,
		String workType,
		WorkTypeTemplate workTypeTemplate,
		LocalDate workDate,
		String targetType,
		Long targetId,
		String materialName,
		String dilutionRatio,
		String quantity,
		String worker,
		Long fromBedZoneId,
		Long toBedZoneId,
		Map<String, Object> details,
		String memo,
		WorkRecordStatus status,
		LocalDateTime canceledAt,
		String cancelReason) {

	public static WorkRecordResponse from(WorkRecord workRecord, WorkTypeTemplate template) {
		return new WorkRecordResponse(
				workRecord.getId(),
				workRecord.getWorkTypeRef() == null ? null : workRecord.getWorkTypeRef().getId(),
				workRecord.getWorkType(),
				template,
				workRecord.getWorkDate(),
				workRecord.getTargetType(),
				workRecord.getTargetId(),
				workRecord.getMaterialName(),
				workRecord.getDilutionRatio(),
				workRecord.getQuantity(),
				workRecord.getWorker(),
				workRecord.getFromBedZoneId(),
				workRecord.getToBedZoneId(),
				workRecord.getDetails(),
				workRecord.getMemo(),
				workRecord.getStatus(),
				TimeConfig.toFarmTime(workRecord.getCanceledAt()),
				workRecord.getCancelReason());
	}
}
