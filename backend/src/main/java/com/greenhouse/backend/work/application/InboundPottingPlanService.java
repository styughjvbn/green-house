package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.dto.InboundPottingCandidateResponse;
import com.greenhouse.backend.work.dto.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
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
		return inboundPottingPlanGateway.findCandidates().stream()
				.map(InboundPottingCandidateResponse::from)
				.toList();
	}

	public WorkOperationResponse create(InboundPottingPlanCreateRequest request) {
		support.validateDates(request.plannedStartDate(), request.plannedEndDate());
		WorkType workType = workTypeService.getByCode(WorkType.POTTING_CODE);
		if (!workType.isActive()) {
			throw new IllegalArgumentException("포트 작업 유형이 비활성화되어 있습니다.");
		}
		List<Long> requestedIds = request.inboundRecordIds().stream().distinct().toList();
		for (Long inboundRecordId : requestedIds) {
			if (!executionRepository.findActiveInboundPottingForUpdate(inboundRecordId).isEmpty()) {
				throw new IllegalArgumentException("이미 활성 포트 작업 계획에 포함된 입고 기록입니다.");
			}
		}
		List<InboundPottingPlanTarget> records = inboundPottingPlanGateway.resolve(requestedIds);
		Long varietyId = records.getFirst().varietyId();
		if (varietyId == null || records.stream().anyMatch(record -> !varietyId.equals(record.varietyId()))) {
			throw new IllegalArgumentException("포트 작업은 하나의 품종만 대상으로 계획할 수 있습니다.");
		}

		WorkOperation operation = new WorkOperation(
				workType,
				support.normalizeRequired(request.title()),
				request.plannedStartDate(),
				request.plannedEndDate(),
				WorkSourceScopeType.INBOUND_RECORD_SELECTION,
				null,
				Map.of("inboundRecordIds", requestedIds),
				Map.of(),
				support.normalize(request.worker()),
				support.normalize(request.memo()),
				support.now());
		List<InboundPottingPlanTarget> orderedRecords = records.stream()
				.sorted(Comparator.comparing(record -> requestedIds.indexOf(record.id())))
				.toList();
		aggregateCreator.createForInboundRecords(operation, orderedRecords);
		inboundPottingPlanGateway.markPottingPlanned(requestedIds);
		return queryService.get(operation.getId());
	}

	public List<WorkOperationResponse> createBatch(InboundPottingPlanBatchCreateRequest request) {
		InboundPottingPlanCreateRequest planRequest = request.plan();
		List<Long> requestedIds = planRequest.inboundRecordIds().stream().distinct().toList();
		if (requestedIds.isEmpty()) {
			throw new IllegalArgumentException("포트 작업할 입고 기록이 한 개 이상 필요합니다.");
		}
		List<InboundPottingPlanTarget> records = inboundPottingPlanGateway.resolve(requestedIds);
		Map<String, List<Long>> idsByVariety = new LinkedHashMap<>();
		Map<String, String> namesByVariety = new LinkedHashMap<>();
		for (InboundPottingPlanTarget record : records) {
			String key = record.varietyId() == null
					? "name:" + record.varietyName()
					: "id:" + record.varietyId();
			idsByVariety.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record.id());
			namesByVariety.putIfAbsent(key, record.varietyName());
		}
		int varietyCount = idsByVariety.size();
		return idsByVariety.entrySet().stream()
				.map(entry -> create(new InboundPottingPlanCreateRequest(
						varietyTitle(planRequest.title(), namesByVariety.get(entry.getKey()), varietyCount),
						planRequest.plannedStartDate(),
						planRequest.plannedEndDate(),
						entry.getValue(),
						planRequest.worker(),
						planRequest.memo())))
				.toList();
	}

	private String varietyTitle(String baseTitle, String varietyName, int varietyCount) {
		if (varietyCount <= 1 || varietyName == null || varietyName.isBlank()) {
			return support.normalizeRequired(baseTitle);
		}
		return support.normalizeRequired(baseTitle) + " - " + varietyName;
	}
}
