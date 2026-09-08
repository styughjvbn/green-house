package com.greenhouse.backend.farm.application.orchid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.application.orchid.mutation.MoveOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.transformation.StructureChangeExecutor;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MovementWorkHandler implements WorkEffectHandler {

	private final StructureChangeExecutor structureChangeExecutor;

	private final OrchidGroupRepository orchidGroupRepository;

	private final OrchidGroupMutationEngine mutationEngine;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Override
	public String supports() {
		return "MOVE";
	}

	@Override
	public WorkEffectKind effectKind() {
		return WorkEffectKind.ATTRIBUTE_CHANGE;
	}

	@Override
	public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
		var target = context.target();
		if (command.payload() instanceof StructureChangeCommand request) {
			return structureChangeExecutor.execute(context, request, command.placementExclusionOrchidGroupIds());
		}
		if (target == null || target.referenceType() != WorkTargetReferenceType.ORCHID_GROUP) {
			throw new IllegalArgumentException("자리 이동 작업에는 난 묶음 대상이 필요합니다.");
		}
		OrchidGroupMoveRequest request = command.payload() == null
				? objectMapper.convertValue(command.resultDetails(), OrchidGroupMoveRequest.class)
				: command.payloadAs(OrchidGroupMoveRequest.class);
		var moved = moveWithEngine(context, target.orchidGroupId(), command, request);
		var details = new WorkEffectResults.Moved(target.orchidGroupId(), target.locationSnapshot().get("bedZoneId"),
				moved.bedZoneId(), moved.startPosition(), moved.endPosition())
			.toMap();
		return new WorkExecutionResult("MOVE", details, List.of(target.orchidGroupId()), moved.mutationLink());
	}

	private MovedGroup moveWithEngine(WorkEffectContext context, Long orchidGroupId, WorkEffectCommand command,
			OrchidGroupMoveRequest request) {
		var current = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new IllegalArgumentException("이동할 난 묶음을 찾을 수 없습니다."));
		if (current.getBedZone().getId().equals(request.toBedZoneId())
				&& equalPosition(current.getStartPosition(), request.startPosition())
				&& equalPosition(current.getEndPosition(), request.endPosition())) {
			return new MovedGroup(current.getBedZone().getId(), current.getStartPosition(), current.getEndPosition(),
					null);
		}
		var mutation = mutationEngine.move(new MoveOrchidGroupMutationCommand(
				OrchidGroupMutationSources.work(context.operationId(), command.effectKey()), orchidGroupId,
				request.toBedZoneId(), request.startPosition(), request.endPosition(), context.plannedStartDate(),
				request.memo()));
		var group = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new IllegalArgumentException("이동한 난 묶음을 찾을 수 없습니다."));
		return new MovedGroup(group.getBedZone().getId(), group.getStartPosition(), group.getEndPosition(),
				new WorkMutationLink(mutation.mutationId(), mutation.correlationId()));
	}

	private boolean equalPosition(java.math.BigDecimal current, java.math.BigDecimal requested) {
		if (current == null || requested == null) {
			return current == null && requested == null;
		}
		return current.compareTo(requested) == 0;
	}

	private record MovedGroup(Long bedZoneId, java.math.BigDecimal startPosition, java.math.BigDecimal endPosition,
			WorkMutationLink mutationLink) {
	}

}
