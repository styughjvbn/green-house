package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.dto.WorkRecordCreateRequest;
import com.greenhouse.backend.work.dto.WorkRecordResponse;
import com.greenhouse.backend.work.repository.WorkRecordRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkRecordService {

	private final WorkRecordRepository workRecordRepository;
	private final WorkTypeService workTypeService;
	private final WorkTargetValidator workTargetValidator;

	@Transactional(readOnly = true)
	public List<WorkRecordResponse> getWorkRecords(
			String targetType,
			Long targetId,
			String workType,
			LocalDate from,
			LocalDate to) {
		if (targetType != null) {
			validateTarget(targetType, targetId);
		}
		return workRecordRepository.search(normalize(targetType), targetId, normalize(workType), from, to).stream()
				.map(this::toResponse)
				.toList();
	}

	public WorkRecordResponse create(WorkRecordCreateRequest request) {
		var workType = workTypeService.getActiveForCreate(request.workTypeId());
		validateTarget(request.targetType(), request.targetId());
		var workRecord = new WorkRecord(
				workType,
				request.workDate(),
				normalizeRequired(request.targetType()),
				request.targetId(),
				normalize(request.materialName()),
				normalize(request.dilutionRatio()),
				normalize(request.quantity()),
				normalize(request.worker()),
				normalize(request.memo()));
		return toResponse(workRecordRepository.save(workRecord));
	}

	private WorkRecordResponse toResponse(WorkRecord workRecord) {
		return WorkRecordResponse.from(
				workRecord,
				workTypeService.resolveTemplate(workRecord.getWorkTypeRef(), workRecord.getWorkType()));
	}

	private void validateTarget(String targetType, Long targetId) {
		String normalizedTargetType = normalizeRequired(targetType);
		workTargetValidator.validate(normalizedTargetType, targetId);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("Required text value is empty.");
		}
		return normalized;
	}
}
