package com.greenhouse.backend.farm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.MergeSourceInputRequest;
import com.greenhouse.backend.farm.dto.MergeWorkOperationRequest;
import com.greenhouse.backend.farm.dto.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.StructureChangeReferenceReader;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MergeWorkHandler implements WorkEffectHandler {

	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupCommandService orchidGroupCommandService;
	private final OrchidGroupLineageService lineageService;
	private final StructureChangeReferenceReader structureChangeReferenceReader;
	private final StructureChangeExecutor structureChangeExecutor;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public MergeWorkHandler(
			OrchidGroupRepository orchidGroupRepository,
			OrchidGroupCommandService orchidGroupCommandService,
			OrchidGroupLineageService lineageService,
			StructureChangeReferenceReader structureChangeReferenceReader,
			StructureChangeExecutor structureChangeExecutor) {
		this.orchidGroupRepository = orchidGroupRepository;
		this.orchidGroupCommandService = orchidGroupCommandService;
		this.lineageService = lineageService;
		this.structureChangeReferenceReader = structureChangeReferenceReader;
		this.structureChangeExecutor = structureChangeExecutor;
	}

	@Override public String supports() { return "MERGE"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (command.payload() instanceof com.greenhouse.backend.work.dto.StructureChangeExecutionRequest request) {
			return structureChangeExecutor.execute(operation, request);
		}
		if (target != null) {
			throw new IllegalArgumentException("합식은 작업 전체 대상을 한 번에 실행해야 합니다.");
		}
		MergeWorkOperationRequest request = objectMapper.convertValue(
				command.resultDetails(), MergeWorkOperationRequest.class);
		validateRequest(operation, request);

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
		if (request.result().quantity() + request.lossQuantity() != totalInput) {
			throw new IllegalArgumentException("합식 투입 수량은 결과 수량과 손실 수량의 합과 같아야 합니다.");
		}
		if (request.lossQuantity() > 0
				&& (request.lossReason() == null || request.lossReason().isBlank())) {
			throw new IllegalArgumentException("손실 수량이 있으면 손실 사유를 입력해야 합니다.");
		}

		String resultStatus = first.getStatus();
		inputBySourceId.forEach((sourceId, inputQuantity) ->
				sourcesById.get(sourceId).applyRepot(inputQuantity));
		var row = request.result();
		OrchidGroup result = orchidGroupCommandService.createEntity(new OrchidGroupCreateRequest(
				row.bedZoneId(), varietyId, row.quantity(), row.potSize(), row.ageYear(), resultStatus,
				row.placementType(), row.trayCount(), row.splitPlacementAllowed(),
				row.startPosition(), row.endPosition(), row.memo()));
		request.sources().forEach(sourceInput -> lineageService.record(
				sourcesById.get(sourceInput.sourceOrchidGroupId()), result,
				OrchidGroupLineageRelationType.MERGED_TO, operation,
				sourceInput.inputQuantity(), result.getQuantity()));

		var details = new LinkedHashMap<String, Object>();
		details.put("sourceOrchidGroupIds", sourceIds);
		details.put("sourceInputQuantities", inputBySourceId);
		details.put("totalInputQuantity", totalInput);
		details.put("lossQuantity", request.lossQuantity());
		if (request.lossReason() != null && !request.lossReason().isBlank()) {
			details.put("lossReason", request.lossReason().trim());
		}
		details.put("resultOrchidGroupId", result.getId());
		return new WorkExecutionResult("MERGE", details, List.of(result.getId()));
	}

	private void validateRequest(WorkOperation operation, MergeWorkOperationRequest request) {
		if (request == null || request.sources() == null || request.sources().size() < 2) {
			throw new IllegalArgumentException("합식은 원본 난 묶음이 두 개 이상 필요합니다.");
		}
		if (request.sources().stream().anyMatch(source -> source == null
				|| source.sourceOrchidGroupId() == null
				|| source.inputQuantity() == null
				|| source.inputQuantity() < 1)) {
			throw new IllegalArgumentException("합식 원본 ID와 투입 수량을 확인해주세요.");
		}
		if (request.result() == null || request.lossQuantity() == null || request.lossQuantity() < 0) {
			throw new IllegalArgumentException("합식 결과와 손실 수량이 필요합니다.");
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
