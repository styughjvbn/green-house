package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.application.orchid.mutation.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.correction.OrchidGroupCorrectionInput;
import com.greenhouse.backend.work.application.correction.StructureChangeReferenceReader;
import com.greenhouse.backend.work.application.correction.WorkCorrectionCommand;
import com.greenhouse.backend.work.application.correction.WorkCorrectionPort;
import com.greenhouse.backend.work.application.correction.WorkOperationDateCorrectionService;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmWorkCorrectionAdapter implements WorkCorrectionPort {

	private final StructureChangeReferenceReader structureChangeReferenceReader;

	private final WorkOperationDateCorrectionService workOperationDateCorrectionService;

	private final OrchidGroupRepository orchidGroupRepository;

	private final List<OrchidGroupUsageInspector> usageInspectors;

	private final OrchidGroupMutationEngine mutationEngine;

	@Override
	public WorkExecutionResult correct(Long originalOperationId, java.util.function.Supplier<Long> correctionId,
			WorkCorrectionCommand request) {
		List<Long> correctableIds = structureChangeReferenceReader
			.getCorrectableResultOrchidGroupIds(originalOperationId);
		Set<Long> adjustmentIds = request.orchidGroupAdjustments()
			.stream()
			.map(OrchidGroupCorrectionInput::orchidGroupId)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (adjustmentIds.size() != request.orchidGroupAdjustments().size()) {
			throw new IllegalArgumentException("같은 난 묶음을 한 보정에서 중복 지정할 수 없습니다.");
		}
		if (request.cancelResultCreation() && adjustmentIds.size() != 1) {
			throw new IllegalArgumentException("결과 생성 취소는 한 번에 하나의 난 묶음만 처리할 수 있습니다.");
		}
		if (!new LinkedHashSet<>(correctableIds).containsAll(adjustmentIds)) {
			throw new IllegalArgumentException("원본 구조 변경 작업이 만든 결과 난 묶음만 보정할 수 있습니다.");
		}
		Map<Long, OrchidGroup> groupsById = orchidGroupRepository.findAllForUpdateByIdIn(adjustmentIds)
			.stream()
			.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (groupsById.size() != adjustmentIds.size()) {
			throw new NotFoundException("보정 대상 난 묶음 일부를 찾을 수 없습니다.");
		}
		Set<Long> changedAdjustmentIds = changedAdjustmentIds(request, adjustmentIds, groupsById);
		boolean workDateChanged = !workOperationDateCorrectionService.getWorkDate(originalOperationId)
			.equals(request.workDate());
		if (request.cancelResultCreation() && workDateChanged) {
			throw new IllegalArgumentException("결과 생성 취소와 작업일 보정은 별도로 처리해야 합니다.");
		}
		if (changedAdjustmentIds.isEmpty() && !workDateChanged) {
			throw new IllegalArgumentException("수량, 상태 또는 작업일 중 현재 값과 다른 보정 값이 필요합니다.");
		}
		var blockers = usageInspectors.stream()
			.flatMap(inspector -> inspector.inspect(changedAdjustmentIds, originalOperationId).stream())
			.toList();
		if (!blockers.isEmpty()) {
			throw new IllegalArgumentException(blockers.getFirst().message());
		}

		List<WorkEffectResults.Adjustment> auditRows = request.orchidGroupAdjustments()
			.stream()
			.filter(adjustment -> changedAdjustmentIds.contains(adjustment.orchidGroupId()))
			.map(adjustment -> {
				OrchidGroup group = groupsById.get(adjustment.orchidGroupId());
				if (request.cancelResultCreation()) {
					return new WorkEffectResults.Adjustment(group.getId(), group.getQuantity(), group.getStatus(), 0,
							OrchidGroupStatusPolicy.CREATION_CANCELED);
				}
				return new WorkEffectResults.Adjustment(group.getId(), group.getQuantity(), group.getStatus(),
						adjustment.quantity(), adjustment.status().trim());
			})
			.toList();
		Long eventId = correctionId.get();
		WorkMutationLink mutationLink = null;
		if (!changedAdjustmentIds.isEmpty()) {
			var references = structureChangeReferenceReader.getMutationReferences(originalOperationId,
					changedAdjustmentIds);
			RelatedOrchidGroupMutations related = references.legacySource() ? RelatedOrchidGroupMutations.legacy()
					: RelatedOrchidGroupMutations.current(references.mutationIds());
			var source = OrchidGroupMutationSources.workCorrection(eventId);
			var mutation = request.cancelResultCreation()
					? mutationEngine.cancelCreation(new CancelOrchidGroupCreationMutationCommand(source,
							changedAdjustmentIds.iterator().next(), related, request.workDate(), request.reason()))
					: mutationEngine.correct(new CorrectOrchidGroupsMutationCommand(source,
							request.orchidGroupAdjustments()
								.stream()
								.filter(adjustment -> changedAdjustmentIds.contains(adjustment.orchidGroupId()))
								.map(adjustment -> new CorrectOrchidGroupMutationItem(adjustment.orchidGroupId(),
										adjustment.quantity(), adjustment.status()))
								.toList(),
							related, request.workDate(), request.reason()));
			mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());

		}
		var dateCorrection = workOperationDateCorrectionService.correct(originalOperationId, request.workDate());
		var resultDetails = new WorkEffectResults.Corrected(originalOperationId, dateCorrection.before(),
				dateCorrection.after(), auditRows)
			.toMap();
		return new WorkExecutionResult("CORRECTION", resultDetails, List.copyOf(changedAdjustmentIds), mutationLink);
	}

	private Set<Long> changedAdjustmentIds(WorkCorrectionCommand request, Set<Long> adjustmentIds,
			Map<Long, OrchidGroup> groupsById) {
		if (request.cancelResultCreation()) {
			OrchidGroup group = groupsById.get(adjustmentIds.iterator().next());
			if (OrchidGroupStatusPolicy.CREATION_CANCELED.equals(group.getStatus())) {
				throw new IllegalArgumentException("이미 생성 취소된 결과 난 묶음입니다.");
			}
			return adjustmentIds;
		}
		return request.orchidGroupAdjustments().stream().filter(adjustment -> {
			OrchidGroup group = groupsById.get(adjustment.orchidGroupId());
			if (OrchidGroupStatusPolicy.CREATION_CANCELED.equals(group.getStatus())) {
				throw new IllegalArgumentException("생성 취소된 결과 난 묶음은 다시 보정할 수 없습니다.");
			}
			return !group.getQuantity().equals(adjustment.quantity())
					|| !group.getStatus().equals(adjustment.status().trim());
		}).map(OrchidGroupCorrectionInput::orchidGroupId).collect(Collectors.toCollection(LinkedHashSet::new));
	}

}
