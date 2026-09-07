package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.application.orchid.mutation.DiscardOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — Engine 경로와 전환 후 제거할 직접 폐기 분기를 함께 가진다.
 * Removal gate: 운영 ACTIVE 안정화 및 writer inventory 승인.
 */
@Component
@RequiredArgsConstructor
public class DiscardWorkHandler implements WorkEffectHandler {

	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupMutationEngine mutationEngine;
	private final OrchidGroupMutationRoutingPolicy mutationRoutingPolicy;

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
		OrchidGroup orchidGroup = orchidGroupRepository
				.findAllForUpdateByIdIn(List.of(target.orchidGroupId()))
				.stream()
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("폐기할 난 묶음을 찾을 수 없습니다."));
		int beforeQuantity = orchidGroup.getQuantity();
		String beforeStatus = orchidGroup.getStatus();
		WorkMutationLink mutationLink = null;
		var mutationCommand = mutationRoutingPolicy.routesToEngine()
				? new DiscardOrchidGroupMutationCommand(
				OrchidGroupMutationSources.work(context.operationId(), command.effectKey()),
				orchidGroup.getId(),
				discardQuantity,
				context.plannedStartDate(),
				reason(command.resultDetails()))
				: null;
		if (mutationRoutingPolicy.routesToEngine()) {
			var mutation = mutationEngine.discard(mutationCommand);
			mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
		} else {
			orchidGroup.discard(discardQuantity);
		}

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
		return new WorkExecutionResult(
				"DISCARD", details, List.of(orchidGroup.getId()), mutationLink);
	}

	private String reason(Map<String, Object> details) {
		Object value = details == null ? null : details.get("reason");
		return value instanceof String reason && !reason.isBlank()
				? reason.trim()
				: "폐기 작업 실행";
	}

	private int readDiscardQuantity(Map<String, Object> details) {
		Object value = details == null ? null : details.get("discardQuantity");
		if (!(value instanceof Number number) || number.intValue() < 1) {
			throw new IllegalArgumentException("폐기 수량은 1 이상이어야 합니다.");
		}
		return number.intValue();
	}
}
