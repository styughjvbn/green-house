package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.application.target.InboundPottingPlanTarget;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.dto.effect.InboundPottingCandidateResponse;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundPottingPlanService {

	private final WorkTypeService workTypeService;

	private final WorkTargetExecutionRepository executionRepository;

	private final InboundPottingPlanGateway inboundPottingPlanGateway;

	private final WorkOperationAggregateCreator aggregateCreator;

	private final WorkOperationQueryService queryService;

	private final WorkOperationSupport support;

	@Transactional(readOnly = true)
	public List<InboundPottingCandidateResponse> getCandidates() {
		return inboundPottingPlanGateway.findCandidates().stream().map(InboundPottingCandidateResponse::from).toList();
	}

	public WorkOperationView create(InboundPottingPlanCreateRequest request) {
		WorkType workType = validatePlan(request);
		List<Long> requestedIds = request.inboundRecordIds().stream().distinct().toList();
		List<InboundPottingPlanTarget> records = inboundPottingPlanGateway.resolveForUpdate(requestedIds);
		validateNoActivePlans(requestedIds);
		Long varietyId = records.getFirst().varietyId();
		if (varietyId == null || records.stream().anyMatch(record -> !varietyId.equals(record.varietyId()))) {
			throw new IllegalArgumentException("포트 작업은 하나의 품종만 대상으로 계획할 수 있습니다.");
		}
		WorkOperation operation = createResolved(request, requestedIds, records, workType);
		inboundPottingPlanGateway.markPottingPlanned(requestedIds);
		return queryService.get(operation.getId());
	}

	public List<WorkOperationView> createBatch(InboundPottingPlanBatchCreateRequest request) {
		InboundPottingPlanCreateRequest planRequest = request.plan();
		List<Long> requestedIds = planRequest.inboundRecordIds().stream().distinct().toList();
		if (requestedIds.isEmpty()) {
			throw new IllegalArgumentException("포트 작업할 입고 기록이 한 개 이상 필요합니다.");
		}
		WorkType workType = validatePlan(planRequest);
		List<InboundPottingPlanTarget> records = inboundPottingPlanGateway.resolveForUpdate(requestedIds);
		validateNoActivePlans(requestedIds);
		Map<String, List<Long>> idsByVariety = new LinkedHashMap<>();
		Map<String, String> namesByVariety = new LinkedHashMap<>();
		for (InboundPottingPlanTarget record : records) {
			String key = record.varietyId() == null ? "name:" + record.varietyName() : "id:" + record.varietyId();
			idsByVariety.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record.id());
			namesByVariety.putIfAbsent(key, record.varietyName());
		}
		int varietyCount = idsByVariety.size();
		Map<Long, InboundPottingPlanTarget> recordsById = records.stream()
			.collect(java.util.stream.Collectors.toMap(InboundPottingPlanTarget::id, record -> record));
		List<Long> operationIds = idsByVariety.entrySet().stream().map(entry -> {
			var groupedRequest = new InboundPottingPlanCreateRequest(
					varietyTitle(planRequest.title(), namesByVariety.get(entry.getKey()), varietyCount),
					planRequest.plannedStartDate(), planRequest.plannedEndDate(), entry.getValue(),
					planRequest.worker(), planRequest.memo());
			var groupedRecords = entry.getValue().stream().map(recordsById::get).toList();
			return createResolved(groupedRequest, entry.getValue(), groupedRecords, workType).getId();
		}).toList();
		inboundPottingPlanGateway.markPottingPlanned(requestedIds);
		return queryService.getAll(operationIds);
	}

	private WorkType validatePlan(InboundPottingPlanCreateRequest request) {
		support.validateDates(request.plannedStartDate(), request.plannedEndDate());
		WorkType workType = workTypeService.getByCode(WorkTypeDefinition.POTTING.name());
		if (!workType.isActive()) {
			throw new IllegalArgumentException("포트 작업 유형이 비활성화되어 있습니다.");
		}
		return workType;
	}

	private void validateNoActivePlans(List<Long> inboundRecordIds) {
		if (!executionRepository.findActiveInboundPottingForUpdate(inboundRecordIds).isEmpty()) {
			throw new IllegalArgumentException("이미 활성 포트 작업 계획에 포함된 입고 기록입니다.");
		}
	}

	private WorkOperation createResolved(InboundPottingPlanCreateRequest request, List<Long> requestedIds,
			List<InboundPottingPlanTarget> records, WorkType workType) {
		WorkOperation operation = new WorkOperation(workType, support.normalizeRequired(request.title()),
				request.plannedStartDate(), request.plannedEndDate(), WorkSourceScopeType.INBOUND_RECORD_SELECTION,
				null, Map.of("inboundRecordIds", requestedIds), Map.of(), support.actor(request.worker()),
				support.normalize(request.memo()), support.now());
		List<InboundPottingPlanTarget> orderedRecords = records.stream()
			.sorted(Comparator.comparing(record -> requestedIds.indexOf(record.id())))
			.toList();
		aggregateCreator.createForInboundRecords(operation, orderedRecords);
		return operation;
	}

	private String varietyTitle(String baseTitle, String varietyName, int varietyCount) {
		if (varietyCount <= 1 || varietyName == null || varietyName.isBlank()) {
			return support.normalizeRequired(baseTitle);
		}
		return support.normalizeRequired(baseTitle) + " - " + varietyName;
	}

}
