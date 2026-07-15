package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkEffectProcessor {

	private final Map<WorkEffectKind, WorkEffectHandler> handlers;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;

	public WorkEffectProcessor(
			List<WorkEffectHandler> handlers,
			WorkAppliedEffectRepository workAppliedEffectRepository) {
		this.handlers = new EnumMap<>(WorkEffectKind.class);
		for (WorkEffectHandler handler : handlers) {
			WorkEffectHandler duplicate = this.handlers.put(handler.supports(), handler);
			if (duplicate != null) {
				throw new IllegalStateException("작업 효과 유형별 처리기는 하나만 등록할 수 있습니다.");
			}
		}
		this.workAppliedEffectRepository = workAppliedEffectRepository;
	}

	public WorkExecutionResult apply(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		WorkEffectKind effectKind = operation.getWorkType().effectKind();
		WorkEffectHandler handler = handlers.get(effectKind);
		if (handler == null) {
			throw new IllegalArgumentException("아직 지원하지 않는 작업 효과 유형입니다: " + effectKind);
		}

		WorkExecutionResult result = handler.execute(operation, target, command);
		workAppliedEffectRepository.save(new WorkAppliedEffect(
				operation,
				target,
				effectKind,
				result.handlerCode(),
				command.executedAt(),
				command.worker(),
				command.resultDetails(),
				result.resultDetails()));
		return result;
	}
}
