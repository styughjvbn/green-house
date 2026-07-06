package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class SystemWorkRecorder {

	private final WorkRecordRepository workRecordRepository;
	private final WorkTypeService workTypeService;

	public SystemWorkRecorder(
		WorkRecordRepository workRecordRepository,
		WorkTypeService workTypeService
	) {
		this.workRecordRepository = workRecordRepository;
		this.workTypeService = workTypeService;
	}

	public void record(
		String workTypeCode,
		LocalDate workDate,
		String targetType,
		Long targetId,
		String materialName,
		String quantity,
		String worker,
		String memo
	) {
		workRecordRepository.save(new WorkRecord(
			workTypeService.getByCode(workTypeCode),
			workDate,
			targetType,
			targetId,
			materialName,
			null,
			quantity,
			worker,
			memo
		));
	}
}
