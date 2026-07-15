package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkEffectProcessor {

	private final Map<String, WorkEffectHandler> handlers;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;
	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

	public WorkEffectProcessor(
			List<WorkEffectHandler> handlers,
			WorkAppliedEffectRepository workAppliedEffectRepository,
			WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository) {
		this.handlers = new HashMap<>();
		for (WorkEffectHandler handler : handlers) {
			WorkEffectHandler duplicate = this.handlers.put(handler.supports(), handler);
			if (duplicate != null) {
				throw new IllegalStateException("작업 효과 handler code는 중복될 수 없습니다.");
			}
		}
		this.workAppliedEffectRepository = workAppliedEffectRepository;
		this.workEffectOrchidGroupRepository = workEffectOrchidGroupRepository;
	}

	public WorkExecutionResult apply(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		String handlerCode = operation.getWorkType().handlerCode();
		WorkEffectHandler handler = handlers.get(handlerCode);
		if (handler == null) {
			throw new IllegalArgumentException("아직 지원하지 않는 작업 효과 handler입니다: " + handlerCode);
		}
		WorkEffectKind effectKind = handler.effectKind();

		WorkExecutionResult result = handler.execute(operation, target, command);
		WorkAppliedEffect appliedEffect = workAppliedEffectRepository.save(new WorkAppliedEffect(
				operation,
				target,
				target == null ? "OPERATION" : "TARGET:" + target.getId(),
				effectKind,
				handlerCode,
				command.executedAt(),
				command.worker(),
				command.resultDetails(),
				result.resultDetails()));
		var groupLinks = new java.util.ArrayList<WorkEffectOrchidGroup>();
		if (target != null && effectKind == WorkEffectKind.STRUCTURE_CHANGE) {
			groupLinks.add(new WorkEffectOrchidGroup(
					appliedEffect, target.getOrchidGroupId(), WorkEffectOrchidGroupRelationType.SOURCE));
		}
		WorkEffectOrchidGroupRelationType resultRelation = target == null
				&& "MULTI_CREATE".equals(handlerCode)
				? WorkEffectOrchidGroupRelationType.CREATED
				: WorkEffectOrchidGroupRelationType.RESULT;
		result.resultOrchidGroupIds().forEach(groupId -> groupLinks.add(
				new WorkEffectOrchidGroup(appliedEffect, groupId, resultRelation)));
		workEffectOrchidGroupRepository.saveAll(groupLinks);
		return result;
	}
}
