package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.target.WorkTargetResolver;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.operation.WorkOperationSearchView;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.dto.operation.OrchidGroupWorkHistoryResponse;
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
		Map<String, Object> currentLocation = workTargetResolver.getCurrent(orchidGroupId).location();
		var historyByOperationId = new LinkedHashMap<Long, OrchidGroupWorkHistoryResponse>();
		targetRepository
				.findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
						orchidGroupId)
				.forEach(target -> historyByOperationId.put(
						target.getWorkOperation().getId(),
						OrchidGroupWorkHistoryResponse.from(target, currentLocation)));
		effectOrchidGroupRepository
				.findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(orchidGroupId)
				.forEach(effectGroup -> historyByOperationId.putIfAbsent(
						effectGroup.getWorkAppliedEffect().getWorkOperation().getId(),
						OrchidGroupWorkHistoryResponse.fromEffect(effectGroup, currentLocation)));
		return historyByOperationId.values().stream()
				.sorted(Comparator.comparing(OrchidGroupWorkHistoryResponse::workDate)
						.reversed()
						.thenComparing(OrchidGroupWorkHistoryResponse::workOperationId, Comparator.reverseOrder()))
				.toList();
	}

	private void validateDates(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("예정 종료일은 예정 시작일보다 빠를 수 없습니다.");
		}
	}
}
