package com.greenhouse.backend.work.application;

import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.dto.MultiCreateWorkOperationRequest;
import com.greenhouse.backend.work.dto.MultiCreateWorkOperationResponse;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MultiCreateWorkOperationService {

	private final WorkTypeService workTypeService;
	private final WorkOperationService workOperationService;
	private final WorkEffectProcessor workEffectProcessor;
	private final WorkOperationRepository workOperationRepository;
	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	public MultiCreateWorkOperationService(
			WorkTypeService workTypeService,
			WorkOperationService workOperationService,
			WorkEffectProcessor workEffectProcessor,
			WorkOperationRepository workOperationRepository,
			WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository) {
		this.workTypeService = workTypeService;
		this.workOperationService = workOperationService;
		this.workEffectProcessor = workEffectProcessor;
		this.workOperationRepository = workOperationRepository;
		this.workEffectOrchidGroupRepository = workEffectOrchidGroupRepository;
	}

	public MultiCreateWorkOperationResponse create(MultiCreateWorkOperationRequest request) {
		String requestKey = normalizeRequired(request.idempotencyKey());
		return workOperationRepository.findByRequestKey(requestKey)
				.map(operation -> response(operation.getId()))
				.orElseGet(() -> execute(request, requestKey));
	}

	@Transactional(readOnly = true)
	public MultiCreateWorkOperationResponse get(Long operationId) {
		return response(operationId);
	}

	private MultiCreateWorkOperationResponse execute(MultiCreateWorkOperationRequest request, String requestKey) {
		WorkType workType = workTypeService.getByCode(WorkType.MULTI_CREATE_CODE);
		WorkOperation operation = new WorkOperation(
				workType,
				normalizeRequired(request.title()),
				request.workDate(),
				request.workDate(),
				WorkSourceScopeType.NONE,
				null,
				Map.of(),
				Map.of("rowCount", request.rows().size()),
				normalize(request.worker()),
				normalize(request.memo()));
		operation.assignRequestKey(requestKey);
		workOperationRepository.save(operation);

		LocalDateTime executedAt = LocalDateTime.now();
		operation.start(executedAt);
		workEffectProcessor.apply(
				operation,
				null,
				new WorkEffectCommand(
						executedAt,
						normalize(request.worker()),
						Map.of("rowCount", request.rows().size()),
						request));
		operation.complete(executedAt);
		return response(operation.getId());
	}

	private MultiCreateWorkOperationResponse response(Long operationId) {
		var groups = workEffectOrchidGroupRepository
				.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId)
				.stream()
				.map(link -> OrchidGroupResponse.from(link.getOrchidGroup()))
				.toList();
		return new MultiCreateWorkOperationResponse(workOperationService.get(operationId), groups);
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
