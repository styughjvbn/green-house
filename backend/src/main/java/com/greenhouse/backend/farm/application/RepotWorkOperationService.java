package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.RepotWorkOperationRequest;
import com.greenhouse.backend.farm.dto.RepotWorkOperationResponse;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.OperationLevelWorkService;
import com.greenhouse.backend.work.domain.WorkType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RepotWorkOperationService {

	private final OperationLevelWorkService operationLevelWorkService;
	private final OrchidGroupRepository orchidGroupRepository;

	public RepotWorkOperationService(
			OperationLevelWorkService operationLevelWorkService,
			OrchidGroupRepository orchidGroupRepository) {
		this.operationLevelWorkService = operationLevelWorkService;
		this.orchidGroupRepository = orchidGroupRepository;
	}

	public RepotWorkOperationResponse execute(RepotWorkOperationRequest request) {
		validateQuantityBalance(request);
		if (orchidGroupRepository.findAllForUpdateByIdIn(List.of(request.sourceOrchidGroupId())).isEmpty()) {
			throw new NotFoundException("원본 난 묶음을 찾을 수 없습니다.");
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("sourceOrchidGroupId", request.sourceOrchidGroupId());
		details.put("inputQuantity", request.inputQuantity());
		details.put("lossQuantity", request.lossQuantity());
		if (request.lossReason() != null && !request.lossReason().isBlank()) {
			details.put("lossReason", request.lossReason().trim());
		}
		details.put("resultCount", request.results().size());
		var operation = operationLevelWorkService.executeForTarget(
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

	private void validateQuantityBalance(RepotWorkOperationRequest request) {
		long resultQuantity = request.results().stream().mapToLong(row -> row.quantity()).sum();
		if (resultQuantity + request.lossQuantity() != request.inputQuantity()) {
			throw new IllegalArgumentException("분갈이 투입 수량은 결과 수량 합계와 손실 수량의 합과 같아야 합니다.");
		}
		if (request.lossQuantity() > 0
				&& (request.lossReason() == null || request.lossReason().isBlank())) {
			throw new IllegalArgumentException("손실 수량이 있으면 손실 사유를 입력해야 합니다.");
		}
	}

	@Transactional(readOnly = true)
	public RepotWorkOperationResponse get(Long operationId) {
		return response(operationId);
	}

	private RepotWorkOperationResponse response(Long operationId) {
		var operation = operationLevelWorkService.get(operationId);
		var resultIds = operationLevelWorkService.getStructureChangeResultOrchidGroupIds(
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
				integerDetail(operation.details(), "lossQuantity"),
				stringDetail(operation.details(), "lossReason"));
	}

	private Integer integerDetail(Map<String, Object> details, String key) {
		Object value = details == null ? null : details.get(key);
		return value instanceof Number number ? number.intValue() : null;
	}

	private String stringDetail(Map<String, Object> details, String key) {
		Object value = details == null ? null : details.get(key);
		return value == null ? null : value.toString();
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
