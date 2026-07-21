package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkEffectProcessor {

	private final Map<String, WorkEffectHandler> handlers;
	private final WorkEffectStore effectStore;

	public WorkEffectProcessor(
			List<WorkEffectHandler> handlers,
			WorkEffectStore effectStore) {
		this.handlers = new HashMap<>();
		for (WorkEffectHandler handler : handlers) {
			WorkEffectHandler duplicate = this.handlers.put(handler.supports(), handler);
			if (duplicate != null) {
				throw new IllegalStateException("작업 효과 handler code는 중복될 수 없습니다.");
			}
		}
		this.effectStore = effectStore;
	}

	public WorkExecutionResult apply(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		return apply(operation, target, command,
				target == null ? "OPERATION" : "TARGET:" + target.getId(),
				target != null && target.getOrchidGroupId() != null
						? List.of(target.getOrchidGroupId())
						: List.of());
	}

	public WorkExecutionResult applyBatch(
			WorkOperation operation,
			String executionKey,
			List<Long> sourceOrchidGroupIds,
			WorkEffectCommand command) {
		return apply(operation, null, command, "EXECUTION:" + executionKey, sourceOrchidGroupIds);
	}

	private WorkExecutionResult apply(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command,
			String effectKey,
			List<Long> sourceOrchidGroupIds) {
		var existing = effectStore.find(operation.getId(), effectKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		String handlerCode = operation.getWorkType().handlerCode();
		WorkEffectHandler handler = handlers.get(handlerCode);
		if (handler == null) {
			throw new IllegalArgumentException("아직 지원하지 않는 작업 효과 handler입니다: " + handlerCode);
		}
		WorkEffectKind effectKind = handler.effectKind();

		WorkExecutionResult result = handler.execute(operation, target, command);
		return persist(operation, target, command, effectKey, sourceOrchidGroupIds, effectKind, result);
	}

	private WorkExecutionResult persist(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command,
			String effectKey,
			List<Long> sourceOrchidGroupIds,
			WorkEffectKind effectKind,
			WorkExecutionResult result) {
		return effectStore.save(
				operation, target, command, effectKey, sourceOrchidGroupIds, effectKind, result);
	}
}
