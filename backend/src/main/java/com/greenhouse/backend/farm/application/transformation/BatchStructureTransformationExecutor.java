package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.application.orchid.mutation.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.effect.StructureChangeSourceInput;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchStructureTransformationExecutor {

	private final OrchidGroupRepository orchidGroupRepository;

	private final OrchidGroupLineageService lineageService;

	private final OrchidGroupMutationEngine mutationEngine;

	public WorkExecutionResult execute(Long operationId, StructureChangeCommand request,
			StructureChangeStrategy strategy, Set<Long> placementExclusionOrchidGroupIds) {
		List<Long> sourceIds = request.sources()
			.stream()
			.map(StructureChangeSourceInput::sourceOrchidGroupId)
			.sorted()
			.toList();
		if (sourceIds.stream().distinct().count() != sourceIds.size()) {
			throw new IllegalArgumentException("작업 원본 난 묶음은 중복될 수 없습니다.");
		}
		Map<Long, OrchidGroup> sources = orchidGroupRepository.findAllForUpdateByIdIn(sourceIds)
			.stream()
			.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		if (sources.size() != sourceIds.size()) {
			throw new NotFoundException("현재 작업할 원본 난 묶음을 모두 찾을 수 없습니다.");
		}
		if (sources.values().stream().anyMatch(source -> source.getVariety() == null)) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 구조 변경할 수 없습니다.");
		}
		OrchidGroup first = sources.get(sourceIds.getFirst());
		Long varietyId = first.getVariety().getId();
		if (!strategy.allowsMixedVarieties() && sources.values()
			.stream()
			.anyMatch(source -> source.getVariety() == null || !varietyId.equals(source.getVariety().getId()))) {
			throw new IllegalArgumentException("한 실행 회차에서는 같은 품종의 난 묶음만 함께 처리할 수 있습니다.");
		}

		Map<Long, StructureChangeSourceInput> sourceRequests = request.sources()
			.stream()
			.collect(Collectors.toMap(StructureChangeSourceInput::sourceOrchidGroupId, Function.identity()));
		Map<Long, Integer> inputBySourceId = sourceRequests.values()
			.stream()
			.collect(Collectors.toMap(StructureChangeSourceInput::sourceOrchidGroupId,
					StructureChangeSourceInput::inputQuantity));
		Map<Long, Integer> transformedBySourceId = strategy.transformedQuantities(request);
		int lossQuantity = Math.max(0, inputBySourceId.values().stream().mapToInt(Integer::intValue).sum()
				- request.results().stream().mapToInt(row -> row.quantity()).sum());
		transformedBySourceId.forEach((sourceId, transformedQuantity) -> {
			if (transformedQuantity > sources.get(sourceId).getQuantity()) {
				throw new IllegalArgumentException("작업 수량은 원본 난 묶음의 현재 수량보다 클 수 없습니다.");
			}
		});
		// Capture inherited attributes before transforming the source groups.
		List<ResultPlan> plannedResults = planResults(request, strategy, sources, first);
		var mutation = mutationEngine.transform(mutationCommand(operationId, request, transformedBySourceId,
				plannedResults, placementExclusionOrchidGroupIds));
		List<Long> resultIds = mutation.entries()
			.stream()
			.filter(entry -> entry.role() == OrchidGroupMutationEntryRole.RESULT)
			.map(entry -> entry.orchidGroupId())
			.toList();
		Map<Long, OrchidGroup> resultsById = orchidGroupRepository.findAllById(resultIds)
			.stream()
			.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		List<OrchidGroup> results = resultIds.stream().map(resultsById::get).toList();
		var mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());

		if (sourceIds.size() == 1) {
			Long sourceId = sourceIds.getFirst();
			for (OrchidGroup result : results) {
				var lineage = lineageService.record(sources.get(sourceId), result, strategy.lineageType(), operationId,
						transformedBySourceId.get(sourceId), result.getQuantity());
				lineage.linkMutation(mutationLink.mutationId());
			}
		}
		var resultRows = java.util.stream.IntStream.range(0, results.size())
			.mapToObj(index -> new WorkEffectResults.ResultGroup(resultIds.get(index), results.get(index).getQuantity(),
					plannedResults.get(index).purpose()))
			.toList();
		var details = new WorkEffectResults.Transformation(request.idempotencyKey(), inputBySourceId, lossQuantity,
				resultRows, sourceIds.size() == 1 ? sources.get(sourceIds.getFirst()).getQuantity() : null)
			.toMap();
		return new WorkExecutionResult(strategy.supports(), details, resultIds, mutationLink);
	}

	private List<ResultPlan> planResults(StructureChangeCommand request, StructureChangeStrategy strategy,
			Map<Long, OrchidGroup> sources, OrchidGroup first) {
		return request.results().stream().map(row -> {
			Long attributeSourceId = row.attributeSourceOrchidGroupId() == null ? first.getId()
					: row.attributeSourceOrchidGroupId();
			OrchidGroup source = sources.get(attributeSourceId);
			if (source == null) {
				throw new IllegalArgumentException("결과 속성 기준 난 묶음은 이번 실행 원본이어야 합니다.");
			}
			boolean inherit = strategy.preservesSourceAttributes();
			StructureChangeResultPurpose purpose = inherit ? StructureChangeResultPurpose.NORMAL : row.purpose();
			return new ResultPlan(new OrchidGroupCreateRequest(row.bedZoneId(), source.getVariety().getId(),
					row.quantity(), inherit ? source.getPotSize() : row.potSize(),
					inherit ? source.getAgeYear() : row.ageYear(), resultStatus(source.getStatus(), purpose),
					row.placementType(), row.trayCount(), row.splitPlacementAllowed(), row.startPosition(),
					row.endPosition(), row.memo()), purpose);
		}).toList();
	}

	private TransformOrchidGroupsMutationCommand mutationCommand(Long operationId, StructureChangeCommand request,
			Map<Long, Integer> transformedBySourceId, List<ResultPlan> plannedResults,
			Set<Long> placementExclusionOrchidGroupIds) {
		List<TransformOrchidGroupMutationSource> mutationSources = request.sources()
			.stream()
			.sorted(java.util.Comparator.comparing(StructureChangeSourceInput::sourceOrchidGroupId))
			.filter(source -> transformedBySourceId.get(source.sourceOrchidGroupId()) > 0)
			.map(source -> new TransformOrchidGroupMutationSource(source.sourceOrchidGroupId(),
					transformedBySourceId.get(source.sourceOrchidGroupId()), source.releasedStartPosition(),
					source.releasedEndPosition()))
			.toList();
		List<TransformOrchidGroupMutationResult> mutationResults = plannedResults.stream().map(plan -> {
			var row = plan.creation();
			return new TransformOrchidGroupMutationResult(row.bedZoneId(),
					new OrchidGroupMutationDetails(row.varietyId(), row.quantity(), row.potSize(), row.ageYear(),
							row.status(), row.placementType(), row.trayCount(), row.splitPlacementAllowed(),
							row.startPosition(), row.endPosition(), row.memo()));
		}).toList();
		return new TransformOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.work(operationId, "EXECUTION:" + request.idempotencyKey()), mutationSources,
				mutationResults, request.completedDate(), request.memo(), placementExclusionOrchidGroupIds);
	}

	private record ResultPlan(OrchidGroupCreateRequest creation, StructureChangeResultPurpose purpose) {
	}

	private String resultStatus(String sourceStatus, StructureChangeResultPurpose purpose) {
		return switch (purpose) {
			case NORMAL -> sourceStatus;
			case DIVIDE_CANDIDATE -> "분주 예정";
			case HELD -> "별도 보관";
		};
	}

}
