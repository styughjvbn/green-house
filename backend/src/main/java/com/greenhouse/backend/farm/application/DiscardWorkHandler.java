package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DiscardWorkHandler implements WorkEffectHandler {

	private final OrchidGroupRepository orchidGroupRepository;

	public DiscardWorkHandler(OrchidGroupRepository orchidGroupRepository) {
		this.orchidGroupRepository = orchidGroupRepository;
	}

	@Override
	public String supports() {
		return "DISCARD";
	}

	@Override
	public WorkEffectKind effectKind() {
		return WorkEffectKind.ATTRIBUTE_CHANGE;
	}

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		if (target == null || target.getTargetReferenceType() != WorkTargetReferenceType.ORCHID_GROUP) {
			throw new IllegalArgumentException("폐기 작업에는 난 묶음 대상이 필요합니다.");
		}
		int discardQuantity = readDiscardQuantity(command.resultDetails());
		OrchidGroup orchidGroup = orchidGroupRepository
				.findAllForUpdateByIdIn(List.of(target.getOrchidGroupId()))
				.stream()
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("폐기할 난 묶음을 찾을 수 없습니다."));
		int beforeQuantity = orchidGroup.getQuantity();
		String beforeStatus = orchidGroup.getStatus();
		orchidGroup.discard(discardQuantity);

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("orchidGroupId", orchidGroup.getId());
		details.put("beforeQuantity", beforeQuantity);
		details.put("discardedQuantity", discardQuantity);
		details.put("remainingQuantity", orchidGroup.getQuantity());
		details.put("beforeStatus", beforeStatus);
		details.put("status", orchidGroup.getStatus());
		if (command.resultDetails() != null) {
			Object reason = command.resultDetails().get("reason");
			if (reason instanceof String value && !value.isBlank()) {
				details.put("reason", value.trim());
			}
		}
		return new WorkExecutionResult("DISCARD", details, List.of(orchidGroup.getId()));
	}

	private int readDiscardQuantity(Map<String, Object> details) {
		Object value = details == null ? null : details.get("discardQuantity");
		if (!(value instanceof Number number) || number.intValue() < 1) {
			throw new IllegalArgumentException("폐기 수량은 1 이상이어야 합니다.");
		}
		return number.intValue();
	}
}
