package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkRecordStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface WorkRecordRepositoryCustom {

	List<WorkRecord> search(
			String targetType,
			Long targetId,
			String workType,
			LocalDate from,
			LocalDate to,
			boolean includeCanceled,
			WorkRecordStatus canceledStatus);

	List<WorkDateRow> findLatestWorkDates(
			String targetType,
			Collection<Long> targetIds,
			WorkRecordStatus status);
}
