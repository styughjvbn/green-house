package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationRequest;
import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationResponse;
import com.greenhouse.backend.farm.dto.MultiCreateCancellationEligibilityResponse;
import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.OperationLevelWorkService;
import com.greenhouse.backend.work.domain.WorkType;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MultiCreateWorkOperationService {

	private final OperationLevelWorkService operationLevelWorkService;
	private final OrchidGroupRepository orchidGroupRepository;
	private final List<OrchidGroupUsageInspector> usageInspectors;

	public MultiCreateWorkOperationService(
			OperationLevelWorkService operationLevelWorkService,
			OrchidGroupRepository orchidGroupRepository,
			List<OrchidGroupUsageInspector> usageInspectors) {
		this.operationLevelWorkService = operationLevelWorkService;
		this.orchidGroupRepository = orchidGroupRepository;
		this.usageInspectors = usageInspectors;
	}

	public MultiCreateWorkOperationResponse create(MultiCreateWorkOperationRequest request) {
		var operation = operationLevelWorkService.execute(
				normalizeRequired(request.idempotencyKey()), WorkType.MULTI_CREATE_CODE,
				normalizeRequired(request.title()), request.workDate(), normalize(request.worker()),
				normalize(request.memo()), Map.of("rowCount", request.rows().size()), request);
		return response(operation.id());
	}

	@Transactional(readOnly = true)
	public MultiCreateWorkOperationResponse get(Long operationId) {
		return response(operationId);
	}

	@Transactional(readOnly = true)
	public MultiCreateCancellationEligibilityResponse getCancellationEligibility(Long operationId) {
		List<Long> groupIds = operationLevelWorkService.getResultOrchidGroupIds(operationId);
		var idSet = new LinkedHashSet<>(groupIds);
		var blockers = usageInspectors.stream()
				.flatMap(inspector -> inspector.inspect(idSet, operationId).stream())
				.map(MultiCreateCancellationEligibilityResponse.Blocker::from)
				.toList();
		return new MultiCreateCancellationEligibilityResponse(
				operationId, blockers.isEmpty(), groupIds, blockers);
	}

	private MultiCreateWorkOperationResponse response(Long operationId) {
		var operation = operationLevelWorkService.get(operationId);
		var ids = operationLevelWorkService.getResultOrchidGroupIds(operationId);
		var groupsById = orchidGroupRepository.findDetailsByIds(ids).stream()
				.collect(java.util.stream.Collectors.toMap(group -> group.getId(), group -> group));
		var groups = ids.stream().filter(groupsById::containsKey)
				.map(id -> OrchidGroupResponse.from(groupsById.get(id))).toList();
		return new MultiCreateWorkOperationResponse(operation, groups);
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
