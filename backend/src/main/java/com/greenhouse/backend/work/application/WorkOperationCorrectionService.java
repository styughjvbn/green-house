package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationCorrection;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.dto.WorkOperationCorrectionCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationCorrectionItemResponse;
import com.greenhouse.backend.work.dto.WorkOperationCorrectionsResponse;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationCorrectionService {

	private final WorkOperationRepository workOperationRepository;
	private final WorkOperationCorrectionRepository correctionRepository;
	private final ImmediateWorkExecutionService immediateWorkExecutionService;
	private final WorkOperationQueryService queryService;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;

	public WorkOperationCorrectionsResponse create(
			Long originalWorkOperationId,
			WorkOperationCorrectionCreateRequest request) {
		WorkOperation original = findCorrectableOriginal(originalWorkOperationId);
		String reason = normalizeRequired(request.reason());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("originalWorkOperationId", originalWorkOperationId);
		details.put("reason", reason);
		var correctionOperation = immediateWorkExecutionService.execute(
				normalizeRequired(request.idempotencyKey()),
				WorkType.CORRECTION_CODE,
				normalizeRequired(request.title()),
				request.workDate(),
				normalize(request.worker()),
				normalize(request.memo()),
				details,
				request);
		WorkOperation correction = workOperationRepository.findWithWorkTypeById(correctionOperation.id())
				.orElseThrow(() -> new NotFoundException("보정 작업을 찾을 수 없습니다."));
		correctionRepository.findByCorrectionWorkOperationId(correction.getId())
				.orElseGet(() -> correctionRepository.save(new WorkOperationCorrection(original, correction, reason)));
		original.markCorrected();
		return response(originalWorkOperationId);
	}

	@Transactional(readOnly = true)
	public WorkOperationCorrectionsResponse get(Long originalWorkOperationId) {
		findCorrectableOriginal(originalWorkOperationId);
		return response(originalWorkOperationId);
	}

	private WorkOperationCorrectionsResponse response(Long originalWorkOperationId) {
		var original = queryService.get(originalWorkOperationId);
		var corrections = correctionRepository
				.findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(originalWorkOperationId).stream()
				.map(correction -> {
					Long correctionOperationId = correction.getCorrectionWorkOperation().getId();
					Map<String, Object> effectDetails = workAppliedEffectRepository
							.findByWorkOperationIdAndEffectKey(correctionOperationId, "OPERATION")
							.map(effect -> effect.getResultDetails())
							.orElse(Map.of());
					return WorkOperationCorrectionItemResponse.from(
							correction,
							queryService.get(correctionOperationId),
							effectDetails);
				})
				.toList();
		return new WorkOperationCorrectionsResponse(original, corrections);
	}

	private WorkOperation findCorrectableOriginal(Long operationId) {
		WorkOperation operation = workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("원본 작업을 찾을 수 없습니다."));
		if (operation.getWorkType().effectKind() != WorkEffectKind.STRUCTURE_CHANGE
				|| operation.getStatus() != WorkOperationStatus.COMPLETED
				&& operation.getStatus() != WorkOperationStatus.CORRECTED) {
			throw new IllegalArgumentException("완료된 구조 변경 작업만 보정할 수 있습니다.");
		}
		return operation;
	}

	private String normalize(String value) {
		if (value == null) return null;
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		return normalized;
	}
}
