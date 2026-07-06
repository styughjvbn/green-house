package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.repository.WorkRecordRepository;
import com.greenhouse.backend.work.repository.WorkDateRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkRecordMetricsReader {
	private final WorkRecordRepository workRecordRepository;

	public WorkRecordMetricsReader(WorkRecordRepository workRecordRepository) {
		this.workRecordRepository = workRecordRepository;
	}

	public Map<Long, LocalDate> getLatestWorkDates(String targetType, Collection<Long> targetIds) {
		if (targetIds == null || targetIds.isEmpty()) {
			return Map.of();
		}
		return workRecordRepository.findLatestWorkDates(targetType, targetIds).stream()
			.collect(Collectors.toMap(WorkDateRow::getTargetId, WorkDateRow::getLatestWorkDate));
	}
}
