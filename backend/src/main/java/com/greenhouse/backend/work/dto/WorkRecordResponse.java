package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import java.time.LocalDate;

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
		String memo) {

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
				workRecord.getMemo());
	}
}
