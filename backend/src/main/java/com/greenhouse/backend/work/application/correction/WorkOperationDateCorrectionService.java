package com.greenhouse.backend.work.application.correction;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.operation.WorkOperationSupport;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkOperationDateCorrectionService {

	private final WorkOperationRepository workOperationRepository;
	private final WorkOperationSupport workOperationSupport;

	public WorkOperationDateCorrectionService(
			WorkOperationRepository workOperationRepository,
			WorkOperationSupport workOperationSupport) {
		this.workOperationRepository = workOperationRepository;
		this.workOperationSupport = workOperationSupport;
	}

	public WorkDateCorrection correct(Long workOperationId, LocalDate workDate) {
		if (workDate.isAfter(workOperationSupport.today())) {
			throw new IllegalArgumentException("보정 작업일은 오늘 이후로 입력할 수 없습니다.");
		}
		var operation = workOperationRepository.findWithWorkTypeById(workOperationId)
				.orElseThrow(() -> new NotFoundException("원본 작업을 찾을 수 없습니다."));
		LocalDate beforeWorkDate = operation.getPlannedStartDate();
		operation.correctWorkDate(workDate);
		return new WorkDateCorrection(beforeWorkDate, operation.getPlannedStartDate());
	}

	@Transactional(readOnly = true)
	public LocalDate getWorkDate(Long workOperationId) {
		return workOperationRepository.findWithWorkTypeById(workOperationId)
				.orElseThrow(() -> new NotFoundException("원본 작업을 찾을 수 없습니다."))
				.getPlannedStartDate();
	}

	public record WorkDateCorrection(LocalDate before, LocalDate after) {
	}
}
