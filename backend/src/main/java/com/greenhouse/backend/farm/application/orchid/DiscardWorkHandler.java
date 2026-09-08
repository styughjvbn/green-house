package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.application.orchid.mutation.DiscardOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscardWorkHandler implements WorkEffectHandler {

	private final OrchidGroupRepository orchidGroupRepository;

	private final OrchidGroupMutationEngine mutationEngine;

	@Override
	public String supports() {
		return "DISCARD";
	}

	@Override
	public WorkEffectKind effectKind() {
		return WorkEffectKind.ATTRIBUTE_CHANGE;
	}

	@Override
	public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
		var target = context.target();
		if (target == null || target.referenceType() != WorkTargetReferenceType.ORCHID_GROUP) {
			throw new IllegalArgumentException("폐기 작업에는 난 묶음 대상이 필요합니다.");
		}
		int discardQuantity = readDiscardQuantity(command.resultDetails());
		OrchidGroup orchidGroup = orchidGroupRepository.findAllForUpdateByIdIn(List.of(target.orchidGroupId()))
			.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("폐기할 난 묶음을 찾을 수 없습니다."));
		int beforeQuantity = orchidGroup.getQuantity();
		String beforeStatus = orchidGroup.getStatus();
		var mutationCommand = new DiscardOrchidGroupMutationCommand(
				OrchidGroupMutationSources.work(context.operationId(), command.effectKey()), orchidGroup.getId(),
				discardQuantity, context.plannedStartDate(), reason(command.resultDetails()));
		var mutation = mutationEngine.discard(mutationCommand);
		var mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());

		Object requestedReason = command.resultDetails() == null ? null : command.resultDetails().get("reason");
		var details = new WorkEffectResults.Discarded(orchidGroup.getId(), beforeQuantity, discardQuantity,
				orchidGroup.getQuantity(), beforeStatus, orchidGroup.getStatus(),
				requestedReason instanceof String value ? value : null)
			.toMap();
		return new WorkExecutionResult("DISCARD", details, List.of(orchidGroup.getId()), mutationLink);
	}

	private String reason(Map<String, Object> details) {
		Object value = details == null ? null : details.get("reason");
		return value instanceof String reason && !reason.isBlank() ? reason.trim() : "폐기 작업 실행";
	}

	private int readDiscardQuantity(Map<String, Object> details) {
		Object value = details == null ? null : details.get("discardQuantity");
		if (!(value instanceof Number number) || number.intValue() < 1) {
			throw new IllegalArgumentException("폐기 수량은 1 이상이어야 합니다.");
		}
		return number.intValue();
	}

}
