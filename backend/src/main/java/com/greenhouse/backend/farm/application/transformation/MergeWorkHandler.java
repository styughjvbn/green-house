package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationShadowService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupsMutationCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.transformation.MergeSourceInputRequest;
import com.greenhouse.backend.farm.dto.transformation.MergeWorkOperationRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.correction.StructureChangeReferenceReader;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeResultRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeSourceRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — Engine 경로와 전환 후 제거할 직접 합식 분기를 함께 가진다.
 * Removal gate: 운영 ACTIVE 안정화 및 writer inventory 승인.
 */
@Component
public class MergeWorkHandler implements WorkEffectHandler {

	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupCommandService orchidGroupCommandService;
	private final StructureChangeReferenceReader structureChangeReferenceReader;
	private final StructureChangeExecutor structureChangeExecutor;
	private final OrchidGroupMutationRoutingPolicy mutationRoutingPolicy;
	private final OrchidGroupMutationShadowService mutationShadowService;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public MergeWorkHandler(
			OrchidGroupRepository orchidGroupRepository,
			OrchidGroupCommandService orchidGroupCommandService,
			StructureChangeReferenceReader structureChangeReferenceReader,
			StructureChangeExecutor structureChangeExecutor,
			OrchidGroupMutationRoutingPolicy mutationRoutingPolicy,
			OrchidGroupMutationShadowService mutationShadowService) {
		this.orchidGroupRepository = orchidGroupRepository;
		this.orchidGroupCommandService = orchidGroupCommandService;
		this.structureChangeReferenceReader = structureChangeReferenceReader;
		this.structureChangeExecutor = structureChangeExecutor;
		this.mutationRoutingPolicy = mutationRoutingPolicy;
		this.mutationShadowService = mutationShadowService;
	}

	@Override public String supports() { return "MERGE"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (command.payload() instanceof com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest request) {
			return structureChangeExecutor.execute(
					operation, request, command.placementExclusionOrchidGroupIds());
		}
		if (target != null) {
			throw new IllegalArgumentException("합식은 작업 전체 대상을 한 번에 실행해야 합니다.");
		}
		MergeWorkOperationRequest request = objectMapper.convertValue(
				command.resultDetails(), MergeWorkOperationRequest.class);
		validateRequest(operation, request);
		if (mutationRoutingPolicy.routesToEngine()) {
			return structureChangeExecutor.execute(operation, toStructureChangeRequest(command, request));
		}

		List<Long> sourceIds = request.sources().stream()
				.map(MergeSourceInputRequest::sourceOrchidGroupId).sorted().toList();
		Map<Long, OrchidGroup> sourcesById = orchidGroupRepository.findAllForUpdateByIdIn(sourceIds).stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (sourcesById.size() != sourceIds.size()) {
			throw new NotFoundException("합식 원본 난 묶음을 찾을 수 없습니다.");
		}
		OrchidGroup first = sourcesById.get(sourceIds.getFirst());
		if (first.getVariety() == null) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 합식할 수 없습니다.");
		}
		Long varietyId = first.getVariety().getId();
		if (sourcesById.values().stream().anyMatch(source ->
				source.getVariety() == null || !varietyId.equals(source.getVariety().getId()))) {
			throw new IllegalArgumentException("합식은 같은 품종의 난 묶음끼리만 할 수 있습니다.");
		}

		Map<Long, Integer> inputBySourceId = request.sources().stream().collect(Collectors.toMap(
				MergeSourceInputRequest::sourceOrchidGroupId,
				MergeSourceInputRequest::inputQuantity));
		inputBySourceId.forEach((sourceId, inputQuantity) -> {
			OrchidGroup source = sourcesById.get(sourceId);
			if (inputQuantity == null || inputQuantity < 1 || inputQuantity > source.getQuantity()) {
				throw new IllegalArgumentException("합식 투입 수량은 각 원본의 현재 수량 이하여야 합니다.");
			}
		});
		int totalInput = inputBySourceId.values().stream().mapToInt(Integer::intValue).sum();
		if (request.result().quantity() > totalInput) {
			throw new IllegalArgumentException("합식 결과 수량은 투입 수량보다 클 수 없습니다.");
		}
		int lossQuantity = totalInput - request.result().quantity();

		String resultStatus = first.getStatus();
		var row = request.result();
		var structureRequest = toStructureChangeRequest(command, request);
		var mutationCommand = mutationRoutingPolicy.usesMutationContract()
				? new TransformOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.work(
						operation.getId(), "EXECUTION:" + structureRequest.idempotencyKey()),
				request.sources().stream()
						.map(source -> new TransformOrchidGroupMutationSource(
								source.sourceOrchidGroupId(), source.inputQuantity(), null, null))
						.toList(),
				List.of(new TransformOrchidGroupMutationResult(
						row.bedZoneId(),
						new OrchidGroupMutationDetails(
								varietyId,
								row.quantity(),
								row.potSize(),
								row.ageYear(),
								resultStatus,
								row.placementType(),
								row.trayCount(),
								row.splitPlacementAllowed(),
								row.startPosition(),
								row.endPosition(),
								row.memo()))),
				structureRequest.completedDate(),
				structureRequest.memo(),
				Set.of())
				: null;
		var shadowPlan = mutationShadowService.prepare(mutationCommand);
		inputBySourceId.forEach((sourceId, inputQuantity) ->
				sourcesById.get(sourceId).applyRepot(inputQuantity));
		OrchidGroup result = orchidGroupCommandService.createEntity(new OrchidGroupCreateRequest(
				row.bedZoneId(), varietyId, row.quantity(), row.potSize(), row.ageYear(), resultStatus,
				row.placementType(), row.trayCount(), row.splitPlacementAllowed(),
				row.startPosition(), row.endPosition(), row.memo()));
		mutationShadowService.completeCreated(shadowPlan, List.of(result.getId()));
		var details = new LinkedHashMap<String, Object>();
		details.put("sourceOrchidGroupIds", sourceIds);
		details.put("sourceInputQuantities", inputBySourceId);
		details.put("totalInputQuantity", totalInput);
		details.put("lossQuantity", lossQuantity);
		details.put("resultOrchidGroupId", result.getId());
		return new WorkExecutionResult("MERGE", details, List.of(result.getId()));
	}

	private StructureChangeExecutionRequest toStructureChangeRequest(
			WorkEffectCommand command,
			MergeWorkOperationRequest request) {
		String executionKey = command.effectKey().startsWith("EXECUTION:")
				? command.effectKey().substring("EXECUTION:".length())
				: command.effectKey();
		var result = request.result();
		return new StructureChangeExecutionRequest(
				executionKey,
				TimeConfig.toFarmTime(command.executedAt()).toLocalDate(),
				command.worker(),
				result.memo(),
				request.sources().stream()
						.map(source -> new StructureChangeSourceRequest(
								source.sourceOrchidGroupId(), source.inputQuantity(), null, null))
						.toList(),
				List.of(new StructureChangeResultRequest(
						result.bedZoneId(),
						result.quantity(),
						null,
						result.potSize(),
						result.ageYear(),
						StructureChangeResultPurpose.NORMAL,
						result.placementType(),
						result.trayCount(),
						result.splitPlacementAllowed(),
						result.startPosition(),
						result.endPosition(),
						result.memo())));
	}

	private void validateRequest(WorkOperation operation, MergeWorkOperationRequest request) {
		if (request == null || request.sources() == null || request.sources().isEmpty()) {
			throw new IllegalArgumentException("합식 원본 난 묶음이 필요합니다.");
		}
		if (request.sources().stream().anyMatch(source -> source == null
				|| source.sourceOrchidGroupId() == null
				|| source.inputQuantity() == null
				|| source.inputQuantity() < 1)) {
			throw new IllegalArgumentException("합식 원본 ID와 투입 수량을 확인해주세요.");
		}
		if (request.result() == null) {
			throw new IllegalArgumentException("합식 결과가 필요합니다.");
		}
		var result = request.result();
		if (result.bedZoneId() == null || result.quantity() == null || result.quantity() < 1
				|| result.startPosition() == null || result.endPosition() == null
				|| result.startPosition().signum() < 0
				|| result.endPosition().compareTo(result.startPosition()) <= 0) {
			throw new IllegalArgumentException("합식 결과의 수량과 배치 위치를 확인해주세요.");
		}
		Set<Long> requestedIds = request.sources().stream()
				.map(MergeSourceInputRequest::sourceOrchidGroupId).collect(Collectors.toSet());
		if (requestedIds.size() != request.sources().size()) {
			throw new IllegalArgumentException("합식 원본 난 묶음은 중복될 수 없습니다.");
		}
		Set<Long> targetIds = structureChangeReferenceReader.getActiveOrchidGroupIds(operation.getId());
		if (!targetIds.equals(requestedIds)) {
			throw new IllegalArgumentException("합식 원본은 계획에 확정된 작업 대상과 일치해야 합니다.");
		}
	}
}
