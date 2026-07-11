package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.repository.WorkRecordRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemWorkRecorder {

	private final WorkRecordRepository workRecordRepository;
	private final WorkTypeService workTypeService;

	public void record(
			String workTypeCode,
			LocalDate workDate,
			String targetType,
			Long targetId,
			String materialName,
			String quantity,
			String worker,
			String memo) {
		record(workTypeCode, workDate, targetType, targetId, materialName, quantity, worker, memo, null);
	}

	public void record(
			String workTypeCode,
			LocalDate workDate,
			String targetType,
			Long targetId,
			String materialName,
			String quantity,
			String worker,
			String memo,
			Map<String, Object> details) {
		workRecordRepository.save(new WorkRecord(
				workTypeService.getByCode(workTypeCode),
				workDate,
				targetType,
				targetId,
				materialName,
				null,
				quantity,
				worker,
				memo,
				details));
	}
}
