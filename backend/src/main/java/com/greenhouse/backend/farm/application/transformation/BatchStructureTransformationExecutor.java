package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeSourceRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BatchStructureTransformationExecutor {

	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupCommandService orchidGroupCommandService;
	private final OrchidGroupLineageService lineageService;

	public BatchStructureTransformationExecutor(
			OrchidGroupRepository orchidGroupRepository,
			OrchidGroupCommandService orchidGroupCommandService,
			OrchidGroupLineageService lineageService) {
		this.orchidGroupRepository = orchidGroupRepository;
		this.orchidGroupCommandService = orchidGroupCommandService;
		this.lineageService = lineageService;
	}

	public WorkExecutionResult execute(
			WorkOperation operation,
			StructureChangeExecutionRequest request,
			StructureChangeStrategy strategy) {
		List<Long> sourceIds = request.sources().stream()
				.map(StructureChangeSourceRequest::sourceOrchidGroupId).sorted().toList();
		if (sourceIds.stream().distinct().count() != sourceIds.size()) {
			throw new IllegalArgumentException("작업 원본 난 묶음은 중복될 수 없습니다.");
		}
		Map<Long, OrchidGroup> sources = orchidGroupRepository.findAllForUpdateByIdIn(sourceIds).stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (sources.size() != sourceIds.size()) {
			throw new NotFoundException("현재 작업할 원본 난 묶음을 모두 찾을 수 없습니다.");
		}
		if (sources.values().stream().anyMatch(source -> source.getVariety() == null)) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 구조 변경할 수 없습니다.");
		}
		OrchidGroup first = sources.get(sourceIds.getFirst());
		Long varietyId = first.getVariety().getId();
		if (!strategy.allowsMixedVarieties() && sources.values().stream().anyMatch(source ->
				source.getVariety() == null || !varietyId.equals(source.getVariety().getId()))) {
			throw new IllegalArgumentException("한 실행 회차에서는 같은 품종의 난 묶음만 함께 처리할 수 있습니다.");
		}

		Map<Long, StructureChangeSourceRequest> sourceRequests = request.sources().stream().collect(Collectors.toMap(
				StructureChangeSourceRequest::sourceOrchidGroupId,
				Function.identity()));
		Map<Long, Integer> inputBySourceId = sourceRequests.values().stream().collect(Collectors.toMap(
				StructureChangeSourceRequest::sourceOrchidGroupId,
				StructureChangeSourceRequest::inputQuantity));
		Map<Long, Integer> transformedBySourceId = strategy.transformedQuantities(request);
		int lossQuantity = Math.max(0, inputBySourceId.values().stream()
				.mapToInt(Integer::intValue).sum() - request.results().stream().mapToInt(row -> row.quantity()).sum());
		Map<Long, String> sourceStatusById = sources.values().stream().collect(Collectors.toMap(
				OrchidGroup::getId,
				OrchidGroup::getStatus));
		var referencedSourceIds = request.results().stream()
				.flatMap(row -> row.sourceOrchidGroupIds() == null || row.sourceOrchidGroupIds().isEmpty()
						? inputBySourceId.keySet().stream()
						: row.sourceOrchidGroupIds().stream())
				.collect(Collectors.toSet());
		if (strategy.requiresEverySourceResult()
				&& !referencedSourceIds.containsAll(inputBySourceId.keySet())) {
			throw new IllegalArgumentException("모든 작업 원본은 결과 난 묶음 한 개 이상에 연결되어야 합니다.");
		}
		transformedBySourceId.forEach((sourceId, transformedQuantity) -> {
			OrchidGroup source = sources.get(sourceId);
			if (transformedQuantity > source.getQuantity()) {
				throw new IllegalArgumentException("작업 수량은 원본 난 묶음의 현재 수량보다 클 수 없습니다.");
			}
		});
		sourceRequests.forEach((sourceId, sourceRequest) -> {
			int transformedQuantity = transformedBySourceId.get(sourceId);
			if (transformedQuantity > 0) {
				sources.get(sourceId).applyRepot(
						transformedQuantity,
						sourceRequest.releasedStartPosition(),
						sourceRequest.releasedEndPosition());
			}
		});

		List<OrchidGroup> results = request.results().stream().map(row -> {
			var lineageSourceIds = row.sourceOrchidGroupIds() == null || row.sourceOrchidGroupIds().isEmpty()
					? inputBySourceId.keySet()
					: row.sourceOrchidGroupIds();
			if (!inputBySourceId.keySet().containsAll(lineageSourceIds)) {
				throw new IllegalArgumentException("결과 난 묶음의 원본은 이번 실행에 포함된 대상이어야 합니다.");
			}
			OrchidGroup resultSource = sources.get(lineageSourceIds.iterator().next());
			Long resultVarietyId = resultSource.getVariety().getId();
			if (lineageSourceIds.stream().anyMatch(sourceId ->
					!resultVarietyId.equals(sources.get(sourceId).getVariety().getId()))) {
				throw new IllegalArgumentException("결과 난 묶음에는 같은 품종의 원본만 연결할 수 있습니다.");
			}
			String resultPotSize = strategy.preservesSourceAttributes()
					? resultSource.getPotSize()
					: row.potSize();
			Integer resultAgeYear = strategy.preservesSourceAttributes()
					? resultSource.getAgeYear()
					: row.ageYear();
			StructureChangeResultPurpose resultPurpose = strategy.preservesSourceAttributes()
					? StructureChangeResultPurpose.NORMAL
					: row.purpose();
			OrchidGroup result = orchidGroupCommandService.createEntity(new OrchidGroupCreateRequest(
					row.bedZoneId(), resultVarietyId, row.quantity(), resultPotSize, resultAgeYear,
					resultStatus(sourceStatusById.get(resultSource.getId()), resultPurpose),
					row.placementType(), row.trayCount(),
					row.splitPlacementAllowed(), row.startPosition(), row.endPosition(), row.memo()));
			lineageSourceIds.forEach(sourceId -> lineageService.record(
					sources.get(sourceId), result, strategy.lineageType(), operation.getId(),
					transformedBySourceId.get(sourceId), result.getQuantity()));
			return result;
		}).toList();

		var details = new LinkedHashMap<String, Object>();
		details.put("executionKey", request.idempotencyKey());
		details.put("sourceInputQuantities", inputBySourceId);
		details.put("lossQuantity", lossQuantity);
		details.put("results", java.util.stream.IntStream.range(0, results.size())
				.mapToObj(index -> Map.of(
						"orchidGroupId", results.get(index).getId(),
						"quantity", results.get(index).getQuantity(),
						"purpose", strategy.preservesSourceAttributes()
								? StructureChangeResultPurpose.NORMAL.name()
								: request.results().get(index).purpose().name()))
				.toList());
		if (sourceIds.size() == 1) {
			Long sourceId = sourceIds.getFirst();
			details.put("sourceOrchidGroupId", sourceId);
			details.put("inputQuantity", inputBySourceId.get(sourceId));
			details.put("remainingQuantity", sources.get(sourceId).getQuantity());
			details.put("resultOrchidGroupIds", results.stream().map(OrchidGroup::getId).toList());
		}
		return new WorkExecutionResult(strategy.supports(), details, results.stream().map(OrchidGroup::getId).toList());
	}

	private String resultStatus(String sourceStatus, StructureChangeResultPurpose purpose) {
		return switch (purpose) {
			case NORMAL -> sourceStatus;
			case DIVIDE_CANDIDATE -> "분주 예정";
			case HELD -> "별도 보관";
		};
	}
}
