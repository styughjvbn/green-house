package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkRecord;
import java.time.LocalDate;

public record WorkRecordResponse(
	Long id,
	String workType,
	LocalDate workDate,
	String targetType,
	Long targetId,
	String materialName,
	String dilutionRatio,
	String quantity,
	String worker,
	Long fromBedZoneId,
	Long toBedZoneId,
	String memo
) {

	public static WorkRecordResponse from(WorkRecord workRecord) {
		return new WorkRecordResponse(
			workRecord.getId(),
			workRecord.getWorkType(),
			workRecord.getWorkDate(),
			workRecord.getTargetType(),
			workRecord.getTargetId(),
			workRecord.getMaterialName(),
			workRecord.getDilutionRatio(),
			workRecord.getQuantity(),
			workRecord.getWorker(),
			workRecord.getFromBedZoneId(),
			workRecord.getToBedZoneId(),
			workRecord.getMemo()
		);
	}
}
