package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.DiscardRecordResultRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import java.time.LocalDate;
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
public class DiscardRecordService {

	private static final String MOVEMENT_DISCARD_REASON = "자리 이동 중 동시 폐기";

	private final WorkOperationPlanService planService;
	private final WorkOperationProgressService progressService;
	private final WorkTypeService workTypeService;

	public WorkOperationResponse create(DiscardRecordCreateRequest request) {
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

	public WorkOperationResponse createForMovement(
			WorkOperation movementOperation,
			LocalDate completedDate,
			String worker,
			String memo,
			Map<Long, Integer> discardQuantities) {
		if (discardQuantities.isEmpty()) {
			return null;
		}
		WorkType discardType = workTypeService.getByCode(WorkType.DISCARD_CODE);
		List<Long> orchidGroupIds = discardQuantities.keySet().stream().sorted().toList();
		Map<String, Object> details = Map.of(
				"movementOperationId", movementOperation.getId(),
				"relation", "MOVEMENT_DISCARD");
		return create(new DiscardRecordCreateRequest(
				new WorkOperationCreateRequest(
						discardType.getId(),
						movementOperation.getTitle() + " - 동시 폐기",
						completedDate,
						completedDate,
						WorkSourceScopeType.MANUAL_SELECTION,
						null,
						null,
						orchidGroupIds,
						details,
						worker,
						memo,
						List.of()),
				completedDate,
				worker,
				orchidGroupIds.stream()
						.map(groupId -> new DiscardRecordResultRequest(
								groupId,
								discardQuantities.get(groupId),
								MOVEMENT_DISCARD_REASON))
						.toList()));
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
