package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class MovementWorkRecorder {
	private final WorkRecordRepository workRecordRepository;
	private final WorkTypeService workTypeService;

	public MovementWorkRecorder(
		WorkRecordRepository workRecordRepository,
		WorkTypeService workTypeService
	) {
		this.workRecordRepository = workRecordRepository;
		this.workTypeService = workTypeService;
	}

	public void record(
		Long orchidGroupId,
		Long fromBedZoneId,
		Long toBedZoneId,
		String worker,
		String memo
	) {
		workRecordRepository.save(WorkRecord.movement(
			workTypeService.getMovementType(),
			orchidGroupId,
			fromBedZoneId,
			toBedZoneId,
			worker,
			memo));
	}
}
