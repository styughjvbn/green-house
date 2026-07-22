package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationRequest;
import com.greenhouse.backend.farm.dto.MultiCreateWorkOperationResponse;
import com.greenhouse.backend.farm.dto.MultiCreateCancellationEligibilityResponse;
import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.application.operation.ImmediateWorkExecutionService;
import com.greenhouse.backend.work.application.operation.WorkOperationQueryService;
import com.greenhouse.backend.work.domain.operation.WorkType;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MultiCreateWorkOperationService {

	private final ImmediateWorkExecutionService immediateWorkExecutionService;
	private final WorkOperationQueryService queryService;
	private final OrchidGroupRepository orchidGroupRepository;
	private final List<OrchidGroupUsageInspector> usageInspectors;
	private final OrchidGroupCollectionMemberRepository memberRepository;

	public MultiCreateWorkOperationService(
			ImmediateWorkExecutionService immediateWorkExecutionService,
			WorkOperationQueryService queryService,
			OrchidGroupRepository orchidGroupRepository,
			List<OrchidGroupUsageInspector> usageInspectors,
			OrchidGroupCollectionMemberRepository memberRepository) {
		this.immediateWorkExecutionService = immediateWorkExecutionService;
		this.queryService = queryService;
		this.orchidGroupRepository = orchidGroupRepository;
		this.usageInspectors = usageInspectors;
		this.memberRepository = memberRepository;
	}

	public MultiCreateWorkOperationResponse create(MultiCreateWorkOperationRequest request) {
		var operation = immediateWorkExecutionService.execute(
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
		if (queryService.get(operationId).status() == WorkOperationStatus.CANCELED) {
			return new MultiCreateCancellationEligibilityResponse(
					operationId, false, immediateWorkExecutionService.getResultOrchidGroupIds(operationId),
					List.of(new MultiCreateCancellationEligibilityResponse.Blocker(
							"ALREADY_CANCELED", "이미 취소된 다중 생성 작업입니다.", 1)));
		}
		List<Long> groupIds = immediateWorkExecutionService.getResultOrchidGroupIds(operationId);
		var idSet = new LinkedHashSet<>(groupIds);
		var blockers = usageInspectors.stream()
				.flatMap(inspector -> inspector.inspect(idSet, operationId).stream())
				.map(MultiCreateCancellationEligibilityResponse.Blocker::from)
				.toList();
		return new MultiCreateCancellationEligibilityResponse(
				operationId, blockers.isEmpty(), groupIds, blockers);
	}

	public MultiCreateWorkOperationResponse cancel(Long operationId) {
		if (queryService.get(operationId).status() == WorkOperationStatus.CANCELED) {
			return response(operationId);
		}
		List<Long> groupIds = immediateWorkExecutionService.getResultOrchidGroupIds(operationId);
		var groups = orchidGroupRepository.findAllForUpdateByIdIn(groupIds);
		if (groups.size() != groupIds.size()) {
			throw new IllegalArgumentException("생성 결과 난 묶음 일부를 찾을 수 없어 취소할 수 없습니다.");
		}
		var idSet = new LinkedHashSet<>(groupIds);
		var blockers = usageInspectors.stream()
				.flatMap(inspector -> inspector.inspect(idSet, operationId).stream())
				.toList();
		if (!blockers.isEmpty()) {
			throw new IllegalArgumentException(blockers.getFirst().message());
		}
		memberRepository.findByOrchidGroupIdInAndRemovedAtIsNull(groupIds)
				.forEach(member -> member.remove());
		groups.forEach(group -> group.cancelCreation());
		immediateWorkExecutionService.cancelMultiCreate(operationId);
		return response(operationId);
	}

	private MultiCreateWorkOperationResponse response(Long operationId) {
		var operation = queryService.get(operationId);
		var ids = immediateWorkExecutionService.getResultOrchidGroupIds(operationId);
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
