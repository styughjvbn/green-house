package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.repository.WorkRecordRepository;
import com.greenhouse.backend.work.domain.WorkRecordStatus;

import lombok.RequiredArgsConstructor;

import com.greenhouse.backend.work.repository.WorkDateRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkRecordMetricsReader {
	private final WorkRecordRepository workRecordRepository;

	public Map<Long, LocalDate> getLatestWorkDates(String targetType, Collection<Long> targetIds) {
		if (targetIds == null || targetIds.isEmpty()) {
			return Map.of();
		}
		return workRecordRepository.findLatestWorkDates(targetType, targetIds, WorkRecordStatus.ACTIVE).stream()
				.collect(Collectors.toMap(WorkDateRow::getTargetId, WorkDateRow::getLatestWorkDate));
	}
}
