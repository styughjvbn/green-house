package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.target.WorkTargetResolver;
import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.operation.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.dto.operation.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.operation.WorkHistoryScopeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkOperationQueryService {

	private final WorkOperationRepository operationRepository;
	private final WorkOperationTargetRepository targetRepository;
	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	private final WorkTargetResolver workTargetResolver;
	private final WorkOperationResponseAssembler responseAssembler;
	private final Clock clock;

	public WorkOperationResponse get(Long operationId) {
		return responseAssembler.assemble(operationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다.")));
	}

	public List<WorkOperationResponse> search(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkOperationSearchView view,
			WorkSourceScopeType scopeType,
			Long scopeId) {
		validateDates(fromDate, toDate);
		if (scopeId != null && scopeType == null) {
			throw new IllegalArgumentException("대상 범위 ID를 조회하려면 대상 범위 유형이 필요합니다.");
		}
		LocalDate farmToday = TimeConfig.farmToday(clock);
		return responseAssembler.assembleAll(operationRepository.search(
				fromDate, toDate, status, view, TimeConfig.farmDayStartUtc(farmToday), scopeType, scopeId));
	}

	public List<OrchidGroupWorkHistoryResponse> getOrchidGroupHistory(Long orchidGroupId) {
		return getWorkHistory(WorkHistoryScopeType.ORCHID_GROUP, orchidGroupId);
	}

	public List<OrchidGroupWorkHistoryResponse> getWorkHistory(
			WorkHistoryScopeType scopeType,
			Long scopeId) {
		validateHistoryScope(scopeType, scopeId);
		WorkSourceScopeType sourceScopeType = scopeType.toSourceScopeType();
		List<Long> directIds = scopeType == WorkHistoryScopeType.ORCHID_GROUP ? List.of(scopeId) : List.of();
		var resolvedTargets = workTargetResolver.resolve(new WorkTargetSelection(
				sourceScopeType, scopeId, null, directIds));
		if (resolvedTargets.isEmpty()) {
			return List.of();
		}
		Set<Long> orchidGroupIds = resolvedTargets.stream()
				.map(target -> target.orchidGroupId())
				.collect(Collectors.toSet());
		Map<Long, Map<String, Object>> currentLocations = resolvedTargets.stream()
				.collect(Collectors.toMap(
						target -> target.orchidGroupId(),
						target -> target.location(),
						(left, right) -> left));
		var historyByOperationId = new LinkedHashMap<Long, OrchidGroupWorkHistoryResponse>();
		targetRepository
				.findByOrchidGroupIdInAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
						orchidGroupIds)
				.forEach(target -> historyByOperationId.put(
						target.getWorkOperation().getId(),
						OrchidGroupWorkHistoryResponse.from(
								target,
								currentLocations.getOrDefault(target.getOrchidGroupId(), target.getLocationSnapshot()))));
		effectOrchidGroupRepository
				.findByOrchidGroupIdInOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(orchidGroupIds)
				.forEach(effectGroup -> historyByOperationId.putIfAbsent(
						effectGroup.getWorkAppliedEffect().getWorkOperation().getId(),
						OrchidGroupWorkHistoryResponse.fromEffect(
								effectGroup,
								currentLocations.get(effectGroup.getOrchidGroupId()))));
		return historyByOperationId.values().stream()
				.sorted(Comparator.comparing(OrchidGroupWorkHistoryResponse::workDate)
						.reversed()
						.thenComparing(OrchidGroupWorkHistoryResponse::workOperationId, Comparator.reverseOrder()))
				.toList();
	}

	private void validateHistoryScope(WorkHistoryScopeType scopeType, Long scopeId) {
		if (scopeType == null || scopeId == null) {
			throw new IllegalArgumentException("작업 이력 조회 범위 유형과 ID가 필요합니다.");
		}
	}

	private void validateDates(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("예정 종료일은 예정 시작일보다 빠를 수 없습니다.");
		}
	}
}
