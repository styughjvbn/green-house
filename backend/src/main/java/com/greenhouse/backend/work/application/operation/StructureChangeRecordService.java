package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.DiscardRecordResultRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StructureChangeRecordService {

	private static final Set<String> BATCH_STRUCTURE_CODES = Set.of(
			WorkType.REPOT_CODE,
			WorkType.DIVIDE_CODE,
			WorkType.MERGE_CODE);

	private final WorkOperationPlanService planService;
	private final WorkOperationProgressService progressService;
	private final StructureChangeExecutionService structureChangeExecutionService;
	private final InboundPottingPlanService inboundPottingPlanService;
	private final InboundPottingOperationService inboundPottingOperationService;
	private final WorkOperationQueryService queryService;

	public WorkOperationResponse createStructureChangeRecord(StructureChangeRecordCreateRequest request) {
		WorkOperationResponse planned = planService.create(request.operation());
		if (!BATCH_STRUCTURE_CODES.contains(planned.workTypeCode())) {
			throw new IllegalArgumentException("분갈이·분주·합식 작업 기록만 이 방식으로 저장할 수 있습니다.");
		}
		Map<Long, Integer> plannedQuantities = planned.targets().stream()
				.filter(target -> target.orchidGroupId() != null)
				.collect(Collectors.toMap(
						target -> target.orchidGroupId(),
						target -> target.quantitySnapshot()));
		Map<Long, Integer> inputQuantities = request.execution().sources().stream()
				.collect(Collectors.toMap(
						source -> source.sourceOrchidGroupId(),
						source -> source.inputQuantity(),
						(left, right) -> {
							throw new IllegalArgumentException("작업 기록의 원본 난 묶음은 중복될 수 없습니다.");
						}));
		if (!plannedQuantities.equals(inputQuantities)) {
			throw new IllegalArgumentException("작업 기록은 선택한 모든 원본의 전체 수량을 한 번에 처리해야 합니다.");
		}
		progressService.start(planned.id());
		WorkOperationResponse completed = structureChangeExecutionService.execute(
				planned.id(), request.execution());
		if (!"COMPLETED".equals(completed.status().name())) {
			throw new IllegalStateException("구조 변경 작업 기록의 모든 대상을 완료하지 못했습니다.");
		}
		return completed;
	}

	public List<WorkOperationResponse> createStructureChangeRecords(
			StructureChangeRecordBatchCreateRequest request) {
		return request.records().stream()
				.map(this::createStructureChangeRecord)
				.toList();
	}

	public WorkOperationResponse createDiscardRecord(DiscardRecordCreateRequest request) {
		WorkOperationResponse planned = planService.create(request.operation());
		if (!WorkType.DISCARD_CODE.equals(planned.workTypeCode())) {
			throw new IllegalArgumentException("폐기 작업 기록만 이 방식으로 저장할 수 있습니다.");
		}
		Map<Long, DiscardRecordResultRequest> resultByGroupId = request.results().stream()
				.collect(Collectors.toMap(
						DiscardRecordResultRequest::orchidGroupId,
						Function.identity(),
						(left, right) -> {
							throw new IllegalArgumentException("폐기 결과의 난 묶음은 중복될 수 없습니다.");
						},
						LinkedHashMap::new));
		Set<Long> plannedIds = planned.targets().stream()
				.map(target -> target.orchidGroupId())
				.collect(Collectors.toCollection(HashSet::new));
		if (!plannedIds.equals(resultByGroupId.keySet())) {
			throw new IllegalArgumentException("선택한 모든 난 묶음의 폐기 결과를 입력해야 합니다.");
		}

		WorkOperationResponse updated = progressService.start(planned.id());
		for (var target : planned.targets()) {
			DiscardRecordResultRequest result = resultByGroupId.get(target.orchidGroupId());
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("discardQuantity", result.discardQuantity());
			details.put("reason", normalize(result.reason()));
			updated = progressService.completeTarget(
					planned.id(),
					target.id(),
					new WorkTargetExecutionRequest(
							request.worker(),
							details,
							request.completedDate()));
		}
		return updated;
	}

	public List<WorkOperationResponse> createInboundPottingRecord(InboundPottingRecordCreateRequest request) {
		Set<Long> plannedIds = new HashSet<>(request.plan().inboundRecordIds());
		Set<Long> executionIds = request.executions().stream()
				.map(execution -> execution.inboundRecordId())
				.collect(Collectors.toSet());
		if (plannedIds.size() != request.plan().inboundRecordIds().size()
				|| executionIds.size() != request.executions().size()
				|| !plannedIds.equals(executionIds)) {
			throw new IllegalArgumentException("선택한 모든 입고 기록의 포트 작업 결과를 한 번씩 입력해야 합니다.");
		}
		if (request.executions().stream().anyMatch(execution ->
				!request.plan().plannedStartDate().equals(execution.pottingDate()))) {
			throw new IllegalArgumentException("포트 작업 기록의 완료일은 작업일과 같아야 합니다.");
		}

		List<WorkOperationResponse> planned = inboundPottingPlanService.createBatch(
				new InboundPottingPlanBatchCreateRequest(request.plan()));
		request.executions().forEach(inboundPottingOperationService::executeNow);
		return planned.stream().map(operation -> queryService.get(operation.id())).toList();
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
