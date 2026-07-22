package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.application.OrchidGroupUsageInspector;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.correction.StructureChangeReferenceReader;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.dto.correction.OrchidGroupCorrectionRequest;
import com.greenhouse.backend.work.dto.correction.WorkOperationCorrectionCreateRequest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CorrectionWorkHandler implements WorkEffectHandler {

	private final StructureChangeReferenceReader structureChangeReferenceReader;
	private final OrchidGroupRepository orchidGroupRepository;
	private final List<OrchidGroupUsageInspector> usageInspectors;

	public CorrectionWorkHandler(
			StructureChangeReferenceReader structureChangeReferenceReader,
			OrchidGroupRepository orchidGroupRepository,
			List<OrchidGroupUsageInspector> usageInspectors) {
		this.structureChangeReferenceReader = structureChangeReferenceReader;
		this.orchidGroupRepository = orchidGroupRepository;
		this.usageInspectors = usageInspectors;
	}

	@Override public String supports() { return "CORRECTION"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.ATTRIBUTE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (target != null) throw new IllegalArgumentException("보정 작업은 작업 단위로 실행해야 합니다.");
		WorkOperationCorrectionCreateRequest request = command.payloadAs(WorkOperationCorrectionCreateRequest.class);
		Long originalOperationId = originalOperationId(command.resultDetails());
		List<Long> correctableIds = structureChangeReferenceReader
				.getCorrectableResultOrchidGroupIds(originalOperationId);
		Set<Long> adjustmentIds = request.orchidGroupAdjustments().stream()
				.map(OrchidGroupCorrectionRequest::orchidGroupId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (adjustmentIds.size() != request.orchidGroupAdjustments().size()) {
			throw new IllegalArgumentException("같은 난 묶음을 한 보정 작업에서 중복 지정할 수 없습니다.");
		}
		if (!new LinkedHashSet<>(correctableIds).containsAll(adjustmentIds)) {
			throw new IllegalArgumentException("원본 구조 변경 작업이 만든 결과 난 묶음만 보정할 수 있습니다.");
		}
		var blockers = usageInspectors.stream()
				.flatMap(inspector -> inspector.inspect(adjustmentIds, originalOperationId).stream())
				.toList();
		if (!blockers.isEmpty()) {
			throw new IllegalArgumentException(blockers.getFirst().message());
		}

		Map<Long, OrchidGroup> groupsById = orchidGroupRepository.findAllForUpdateByIdIn(adjustmentIds).stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (groupsById.size() != adjustmentIds.size()) {
			throw new NotFoundException("보정 대상 난 묶음 일부를 찾을 수 없습니다.");
		}
		boolean changed = request.orchidGroupAdjustments().stream().anyMatch(adjustment -> {
			OrchidGroup group = groupsById.get(adjustment.orchidGroupId());
			return !group.getQuantity().equals(adjustment.quantity())
					|| !group.getStatus().equals(adjustment.status().trim());
		});
		if (!changed) {
			throw new IllegalArgumentException("현재 값과 다른 보정 값이 하나 이상 필요합니다.");
		}

		List<Map<String, Object>> auditRows = request.orchidGroupAdjustments().stream().map(adjustment -> {
			OrchidGroup group = groupsById.get(adjustment.orchidGroupId());
			Map<String, Object> audit = new LinkedHashMap<>();
			audit.put("orchidGroupId", group.getId());
			audit.put("beforeQuantity", group.getQuantity());
			audit.put("beforeStatus", group.getStatus());
			group.correctQuantityAndStatus(adjustment.quantity(), adjustment.status());
			audit.put("afterQuantity", group.getQuantity());
			audit.put("afterStatus", group.getStatus());
			return audit;
		}).toList();
		Map<String, Object> resultDetails = new LinkedHashMap<>();
		resultDetails.put("originalWorkOperationId", originalOperationId);
		resultDetails.put("adjustments", auditRows);
		return new WorkExecutionResult("CORRECTION", resultDetails, List.copyOf(adjustmentIds));
	}

	private Long originalOperationId(Map<String, Object> details) {
		Object value = details == null ? null : details.get("originalWorkOperationId");
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException("보정 원본 작업 정보가 없습니다.");
		}
		return number.longValue();
	}
}
