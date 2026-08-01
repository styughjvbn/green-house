package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.operation.ImmediateWorkExecutionService;
import com.greenhouse.backend.work.application.operation.WorkOperationQueryService;
import com.greenhouse.backend.work.domain.operation.WorkType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RepotWorkOperationService {

	private final ImmediateWorkExecutionService immediateWorkExecutionService;
	private final WorkOperationQueryService queryService;
	private final OrchidGroupRepository orchidGroupRepository;

	public RepotWorkOperationService(
			ImmediateWorkExecutionService immediateWorkExecutionService,
			WorkOperationQueryService queryService,
			OrchidGroupRepository orchidGroupRepository) {
		this.immediateWorkExecutionService = immediateWorkExecutionService;
		this.queryService = queryService;
		this.orchidGroupRepository = orchidGroupRepository;
	}

	public RepotWorkOperationResponse execute(RepotWorkOperationRequest request) {
		int lossQuantity = calculateLossQuantity(request);
		if (orchidGroupRepository.findAllForUpdateByIdIn(List.of(request.sourceOrchidGroupId())).isEmpty()) {
			throw new NotFoundException("원본 난 묶음을 찾을 수 없습니다.");
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("sourceOrchidGroupId", request.sourceOrchidGroupId());
		details.put("inputQuantity", request.inputQuantity());
		details.put("lossQuantity", lossQuantity);
		details.put("resultCount", request.results().size());
		var operation = immediateWorkExecutionService.executeForTarget(
				normalizeRequired(request.idempotencyKey()),
				WorkType.REPOT_CODE,
				normalizeRequired(request.title()),
				request.workDate(),
				normalize(request.worker()),
				normalize(request.memo()),
				request.sourceOrchidGroupId(),
				details,
				request);
		return response(operation.id());
	}

	private int calculateLossQuantity(RepotWorkOperationRequest request) {
		long resultQuantity = request.results().stream().mapToLong(row -> row.quantity()).sum();
		if (resultQuantity > request.inputQuantity()) {
			throw new IllegalArgumentException("분갈이 결과 수량은 투입 수량보다 클 수 없습니다.");
		}
		return (int) (request.inputQuantity() - resultQuantity);
	}

	@Transactional(readOnly = true)
	public RepotWorkOperationResponse get(Long operationId) {
		return response(operationId);
	}

	private RepotWorkOperationResponse response(Long operationId) {
		var operation = queryService.get(operationId);
		var resultIds = immediateWorkExecutionService.getStructureChangeResultOrchidGroupIds(
				operationId, WorkType.REPOT_CODE);
		var source = orchidGroupRepository.findDetailById(operation.sourceScopeId())
				.orElseThrow(() -> new NotFoundException(
						"원본 난 묶음을 찾을 수 없습니다."));
		var groupsById = orchidGroupRepository.findDetailsByIds(resultIds).stream()
				.collect(java.util.stream.Collectors.toMap(group -> group.getId(), group -> group));
		var results = resultIds.stream()
				.filter(groupsById::containsKey)
				.map(id -> OrchidGroupResponse.from(groupsById.get(id)))
				.toList();
		return new RepotWorkOperationResponse(
				operation,
				OrchidGroupResponse.from(source),
				results,
				integerDetail(operation.details(), "inputQuantity"),
				integerDetail(operation.details(), "lossQuantity"));
	}

	private Integer integerDetail(Map<String, Object> details, String key) {
		Object value = details == null ? null : details.get(key);
		return value instanceof Number number ? number.intValue() : null;
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
