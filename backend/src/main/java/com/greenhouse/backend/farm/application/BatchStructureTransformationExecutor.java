package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.StructureChangeResultPurpose;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.dto.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.dto.StructureChangeSourceRequest;
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
			String handlerCode,
			String workLabel,
			OrchidGroupLineageRelationType relationType) {
		validateBalance(request, workLabel);
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
		OrchidGroup first = sources.get(sourceIds.getFirst());
		if (first.getVariety() == null) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 구조 변경할 수 없습니다.");
		}
		Long varietyId = first.getVariety().getId();
		if (sources.values().stream().anyMatch(source ->
				source.getVariety() == null || !varietyId.equals(source.getVariety().getId()))) {
			throw new IllegalArgumentException("한 실행 회차에서는 같은 품종의 난 묶음만 함께 처리할 수 있습니다.");
		}

		Map<Long, Integer> inputBySourceId = request.sources().stream().collect(Collectors.toMap(
				StructureChangeSourceRequest::sourceOrchidGroupId,
				StructureChangeSourceRequest::inputQuantity));
		var referencedSourceIds = request.results().stream()
				.flatMap(row -> row.sourceOrchidGroupIds() == null || row.sourceOrchidGroupIds().isEmpty()
						? inputBySourceId.keySet().stream()
						: row.sourceOrchidGroupIds().stream())
				.collect(Collectors.toSet());
		if (!referencedSourceIds.containsAll(inputBySourceId.keySet())) {
			throw new IllegalArgumentException("모든 작업 원본은 결과 난 묶음 한 개 이상에 연결되어야 합니다.");
		}
		inputBySourceId.forEach((sourceId, inputQuantity) -> {
			OrchidGroup source = sources.get(sourceId);
			if (inputQuantity > source.getQuantity()) {
				throw new IllegalArgumentException("작업 수량은 원본 난 묶음의 현재 수량보다 클 수 없습니다.");
			}
		});
		inputBySourceId.forEach((sourceId, inputQuantity) ->
				sources.get(sourceId).applyRepot(inputQuantity));

		List<OrchidGroup> results = request.results().stream().map(row -> {
			var lineageSourceIds = row.sourceOrchidGroupIds() == null || row.sourceOrchidGroupIds().isEmpty()
					? inputBySourceId.keySet()
					: row.sourceOrchidGroupIds();
			if (!inputBySourceId.keySet().containsAll(lineageSourceIds)) {
				throw new IllegalArgumentException("결과 난 묶음의 원본은 이번 실행에 포함된 대상이어야 합니다.");
			}
			OrchidGroup resultSource = sources.get(lineageSourceIds.iterator().next());
			OrchidGroup result = orchidGroupCommandService.createEntity(new OrchidGroupCreateRequest(
					row.bedZoneId(), varietyId, row.quantity(), row.potSize(), row.ageYear(),
					resultStatus(resultSource.getStatus(), row.purpose()), row.placementType(), row.trayCount(),
					row.splitPlacementAllowed(), row.startPosition(), row.endPosition(), row.memo()));
			lineageSourceIds.forEach(sourceId -> lineageService.record(
					sources.get(sourceId), result, relationType, operation,
					inputBySourceId.get(sourceId), result.getQuantity()));
			return result;
		}).toList();

		var details = new LinkedHashMap<String, Object>();
		details.put("executionKey", request.idempotencyKey());
		details.put("sourceInputQuantities", inputBySourceId);
		details.put("lossQuantity", request.lossQuantity());
		if (request.lossReason() != null && !request.lossReason().isBlank()) {
			details.put("lossReason", request.lossReason().trim());
		}
		details.put("results", java.util.stream.IntStream.range(0, results.size())
				.mapToObj(index -> Map.of(
						"orchidGroupId", results.get(index).getId(),
						"quantity", results.get(index).getQuantity(),
						"purpose", request.results().get(index).purpose().name()))
				.toList());
		return new WorkExecutionResult(handlerCode, details, results.stream().map(OrchidGroup::getId).toList());
	}

	private void validateBalance(StructureChangeExecutionRequest request, String workLabel) {
		long totalInput = request.sources().stream().mapToLong(StructureChangeSourceRequest::inputQuantity).sum();
		long totalResult = request.results().stream().mapToLong(row -> row.quantity()).sum();
		if (totalInput != totalResult + request.lossQuantity()) {
			throw new IllegalArgumentException(workLabel + " 투입 수량은 결과 수량과 손실 수량의 합과 같아야 합니다.");
		}
		if (request.lossQuantity() > 0
				&& (request.lossReason() == null || request.lossReason().isBlank())) {
			throw new IllegalArgumentException("손실 수량이 있으면 손실 사유를 입력해야 합니다.");
		}
	}

	private String resultStatus(String sourceStatus, StructureChangeResultPurpose purpose) {
		return switch (purpose) {
			case NORMAL -> sourceStatus;
			case DIVIDE_CANDIDATE -> "분주 예정";
			case HELD -> "별도 보관";
		};
	}
}
