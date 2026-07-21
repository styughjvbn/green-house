package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkEffectStore {

	private final WorkAppliedEffectRepository appliedEffectRepository;
	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

	public Optional<WorkExecutionResult> find(Long operationId, String effectKey) {
		return appliedEffectRepository.findByWorkOperationIdAndEffectKey(operationId, effectKey)
				.map(this::toResult);
	}

	public WorkExecutionResult save(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command,
			String effectKey,
			List<Long> sourceOrchidGroupIds,
			WorkEffectKind effectKind,
			WorkExecutionResult result) {
		WorkAppliedEffect appliedEffect = appliedEffectRepository.save(new WorkAppliedEffect(
				operation,
				target,
				effectKey,
				effectKind,
				result.handlerCode(),
				command.executedAt(),
				command.worker(),
				command.resultDetails(),
				result.resultDetails()));
		var groupLinks = new ArrayList<WorkEffectOrchidGroup>();
		if (effectKind == WorkEffectKind.STRUCTURE_CHANGE) {
			sourceOrchidGroupIds.stream().distinct().forEach(groupId -> groupLinks.add(
					new WorkEffectOrchidGroup(
							appliedEffect, groupId, WorkEffectOrchidGroupRelationType.SOURCE)));
		}
		WorkEffectOrchidGroupRelationType resultRelation = target == null
				&& "MULTI_CREATE".equals(result.handlerCode())
				? WorkEffectOrchidGroupRelationType.CREATED
				: WorkEffectOrchidGroupRelationType.RESULT;
		result.resultOrchidGroupIds().forEach(groupId -> groupLinks.add(
				new WorkEffectOrchidGroup(appliedEffect, groupId, resultRelation)));
		effectOrchidGroupRepository.saveAll(groupLinks);
		return result;
	}

	private WorkExecutionResult toResult(WorkAppliedEffect effect) {
		List<Long> resultIds = effectOrchidGroupRepository
				.findByWorkAppliedEffectIdOrderByIdAsc(effect.getId())
				.stream()
				.filter(link -> link.getRelationType() != WorkEffectOrchidGroupRelationType.SOURCE)
				.map(WorkEffectOrchidGroup::getOrchidGroupId)
				.toList();
		return new WorkExecutionResult(effect.getHandlerCode(), effect.getResultDetails(), resultIds);
	}
}
