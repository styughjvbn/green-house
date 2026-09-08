package com.greenhouse.backend.farm.application.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.dto.transformation.MergeSourceInputRequest;
import com.greenhouse.backend.farm.dto.transformation.MergeWorkOperationRequest;
import com.greenhouse.backend.work.application.correction.StructureChangeReferenceReader;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.application.effect.StructureChangeSourceInput;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MergeWorkHandler implements WorkEffectHandler {

	private final StructureChangeReferenceReader structureChangeReferenceReader;

	private final StructureChangeExecutor structureChangeExecutor;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Override
	public String supports() {
		return "MERGE";
	}

	@Override
	public WorkEffectKind effectKind() {
		return WorkEffectKind.STRUCTURE_CHANGE;
	}

	@Override
	public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
		var target = context.target();
		if (command
			.payload() instanceof com.greenhouse.backend.work.application.effect.StructureChangeCommand request) {
			return structureChangeExecutor.execute(context, request, command.placementExclusionOrchidGroupIds());
		}
		if (target != null) {
			throw new IllegalArgumentException("합식은 작업 전체 대상을 한 번에 실행해야 합니다.");
		}
		MergeWorkOperationRequest request = objectMapper.convertValue(command.resultDetails(),
				MergeWorkOperationRequest.class);
		validateRequest(context, request);
		return structureChangeExecutor.execute(context, toStructureChangeRequest(command, request));
	}

	private StructureChangeCommand toStructureChangeRequest(WorkEffectCommand command,
			MergeWorkOperationRequest request) {
		String executionKey = command.effectKey().startsWith("EXECUTION:")
				? command.effectKey().substring("EXECUTION:".length()) : command.effectKey();
		var result = request.result();
		return new StructureChangeCommand(executionKey, TimeConfig.toFarmTime(command.executedAt()).toLocalDate(),
				command.worker(), result.memo(),
				request.sources()
					.stream()
					.map(source -> new StructureChangeSourceInput(source.sourceOrchidGroupId(), source.inputQuantity(),
							null, null))
					.toList(),
				List.of(new StructureChangeResultInput(result.bedZoneId(), result.quantity(), null, result.potSize(),
						result.ageYear(), StructureChangeResultPurpose.NORMAL, result.placementType(),
						result.trayCount(), result.splitPlacementAllowed(), result.startPosition(),
						result.endPosition(), result.memo())));
	}

	private void validateRequest(WorkEffectContext context, MergeWorkOperationRequest request) {
		if (request == null || request.sources() == null || request.sources().isEmpty()) {
			throw new IllegalArgumentException("합식 원본 난 묶음이 필요합니다.");
		}
		if (request.sources()
			.stream()
			.anyMatch(source -> source == null || source.sourceOrchidGroupId() == null || source.inputQuantity() == null
					|| source.inputQuantity() < 1)) {
			throw new IllegalArgumentException("합식 원본 ID와 투입 수량을 확인해주세요.");
		}
		if (request.result() == null) {
			throw new IllegalArgumentException("합식 결과가 필요합니다.");
		}
		var result = request.result();
		if (result.bedZoneId() == null || result.quantity() == null || result.quantity() < 1
				|| result.startPosition() == null || result.endPosition() == null || result.startPosition().signum() < 0
				|| result.endPosition().compareTo(result.startPosition()) <= 0) {
			throw new IllegalArgumentException("합식 결과의 수량과 배치 위치를 확인해주세요.");
		}
		Set<Long> requestedIds = request.sources()
			.stream()
			.map(MergeSourceInputRequest::sourceOrchidGroupId)
			.collect(Collectors.toSet());
		if (requestedIds.size() != request.sources().size()) {
			throw new IllegalArgumentException("합식 원본 난 묶음은 중복될 수 없습니다.");
		}
		Set<Long> targetIds = structureChangeReferenceReader.getActiveOrchidGroupIds(context.operationId());
		if (!targetIds.equals(requestedIds)) {
			throw new IllegalArgumentException("합식 원본은 계획에 확정된 작업 대상과 일치해야 합니다.");
		}
	}

}
