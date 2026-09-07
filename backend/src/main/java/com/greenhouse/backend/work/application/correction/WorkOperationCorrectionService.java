package com.greenhouse.backend.work.application.correction;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.operation.ImmediateWorkExecutionService;
import com.greenhouse.backend.work.application.operation.WorkOperationQueryService;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.dto.correction.WorkOperationCorrectionCreateRequest;
import com.greenhouse.backend.work.dto.correction.WorkOperationCorrectionItemResponse;
import com.greenhouse.backend.work.dto.correction.WorkOperationCorrectionsResponse;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
	private final RequestActorProvider requestActorProvider;

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
				WorkTypeDefinition.CORRECTION.name(),
				normalizeRequired(request.title()),
				request.workDate(),
				requestActorProvider.resolve(request.worker()),
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
		var correctionEntities = correctionRepository
				.findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(originalWorkOperationId);
		if (correctionEntities.isEmpty()) {
			return new WorkOperationCorrectionsResponse(original, java.util.List.of());
		}
		var correctionOperationIds = correctionEntities.stream()
				.map(correction -> correction.getCorrectionWorkOperation().getId())
				.toList();
		var operationsById = queryService.getAll(correctionOperationIds).stream()
				.collect(Collectors.toMap(response -> response.id(), Function.identity()));
		var effectsByOperationId = workAppliedEffectRepository
				.findByWorkOperationIdInAndEffectKey(correctionOperationIds, "OPERATION")
				.stream()
				.collect(Collectors.toMap(effect -> effect.getWorkOperation().getId(), Function.identity()));
		var corrections = correctionEntities.stream()
				.map(correction -> {
					Long correctionOperationId = correction.getCorrectionWorkOperation().getId();
					var effect = effectsByOperationId.get(correctionOperationId);
					return WorkOperationCorrectionItemResponse.from(
							correction,
							operationsById.get(correctionOperationId),
							effect == null ? Map.of() : effect.getResultDetails());
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
