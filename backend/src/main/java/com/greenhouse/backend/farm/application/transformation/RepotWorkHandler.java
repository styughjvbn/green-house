package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import org.springframework.stereotype.Component;

@Component
public class RepotWorkHandler implements WorkEffectHandler {

	private final StructureChangeExecutor structureChangeExecutor;
	private final LegacyStructureChangeRequestMapper legacyRequestMapper;
	private final OrchidGroupCollectionInheritanceService collectionInheritanceService;

	public RepotWorkHandler(
			StructureChangeExecutor structureChangeExecutor,
			LegacyStructureChangeRequestMapper legacyRequestMapper,
			OrchidGroupCollectionInheritanceService collectionInheritanceService) {
		this.structureChangeExecutor = structureChangeExecutor;
		this.legacyRequestMapper = legacyRequestMapper;
		this.collectionInheritanceService = collectionInheritanceService;
	}

	@Override public String supports() { return "REPOT"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (command.payload() instanceof com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest request) {
			return structureChangeExecutor.execute(operation, request);
		}
		if (target == null) throw new IllegalArgumentException("분갈이 작업에는 원본 난 묶음이 필요합니다.");
		RepotWorkOperationRequest request = legacyRequestMapper.read(command);
		if (!target.getOrchidGroupId().equals(request.sourceOrchidGroupId())) {
			throw new IllegalArgumentException("분갈이 작업 대상과 원본 난 묶음이 일치하지 않습니다.");
		}
		var collectionIds = collectionInheritanceService.validate(
				request.sourceOrchidGroupId(), request.inheritCollectionIds());
		var result = structureChangeExecutor.execute(operation, legacyRequestMapper.from(request));
		collectionInheritanceService.inherit(collectionIds, result.resultOrchidGroupIds(), command.worker());
		return result;
	}
}
