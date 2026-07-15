package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OperationLevelWorkService {

	private final WorkTypeService workTypeService;
	private final WorkOperationService workOperationService;
	private final WorkEffectProcessor workEffectProcessor;
	private final WorkOperationRepository workOperationRepository;
	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	public OperationLevelWorkService(
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

	public WorkOperationResponse execute(
			String requestKey,
			String workTypeCode,
			String title,
			LocalDate workDate,
			String worker,
			String memo,
			Map<String, Object> details,
			Object payload) {
		var existing = workOperationRepository.findByRequestKey(requestKey);
		if (existing.isPresent()) return workOperationService.get(existing.get().getId());

		WorkOperation operation = new WorkOperation(
				workTypeService.getByCode(workTypeCode), title, workDate, workDate,
				WorkSourceScopeType.NONE, null, Map.of(), details, worker, memo);
		operation.assignRequestKey(requestKey);
		workOperationRepository.save(operation);
		LocalDateTime executedAt = LocalDateTime.now();
		operation.start(executedAt);
		workEffectProcessor.apply(
				operation, null, new WorkEffectCommand(executedAt, worker, details, payload));
		operation.complete(executedAt);
		return workOperationService.get(operation.getId());
	}

	@Transactional(readOnly = true)
	public WorkOperationResponse get(Long operationId) {
		return workOperationService.get(operationId);
	}

	@Transactional(readOnly = true)
	public List<Long> getResultOrchidGroupIds(Long operationId) {
		var operation = workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new com.greenhouse.backend.common.exception.NotFoundException("작업을 찾을 수 없습니다."));
		if (!com.greenhouse.backend.work.domain.WorkType.MULTI_CREATE_CODE.equals(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("다중 생성 작업만 생성 결과를 조회할 수 있습니다.");
		}
		return workEffectOrchidGroupRepository
				.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId)
				.stream().map(link -> link.getOrchidGroupId()).toList();
	}
}
