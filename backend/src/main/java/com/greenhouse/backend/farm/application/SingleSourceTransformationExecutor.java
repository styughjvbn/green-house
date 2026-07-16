package com.greenhouse.backend.farm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.OrchidGroupCollectionMember;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.RepotWorkOperationRequest;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupCollectionRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SingleSourceTransformationExecutor {

	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupCommandService orchidGroupCommandService;
	private final OrchidGroupLineageService lineageService;
	private final OrchidGroupCollectionRepository collectionRepository;
	private final OrchidGroupCollectionMemberRepository memberRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public SingleSourceTransformationExecutor(
			OrchidGroupRepository orchidGroupRepository,
			OrchidGroupCommandService orchidGroupCommandService,
			OrchidGroupLineageService lineageService,
			OrchidGroupCollectionRepository collectionRepository,
			OrchidGroupCollectionMemberRepository memberRepository) {
		this.orchidGroupRepository = orchidGroupRepository;
		this.orchidGroupCommandService = orchidGroupCommandService;
		this.lineageService = lineageService;
		this.collectionRepository = collectionRepository;
		this.memberRepository = memberRepository;
	}

	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command,
			String workLabel,
			String handlerCode,
			OrchidGroupLineageRelationType relationType) {
		if (target == null) throw new IllegalArgumentException(workLabel + " 작업에는 원본 난 묶음이 필요합니다.");
		RepotWorkOperationRequest request = command.payload() == null
				? objectMapper.convertValue(command.resultDetails(), RepotWorkOperationRequest.class)
				: command.payloadAs(RepotWorkOperationRequest.class);
		if (!target.getOrchidGroupId().equals(request.sourceOrchidGroupId())) {
			throw new IllegalArgumentException(workLabel + " 작업 대상과 원본 난 묶음이 일치하지 않습니다.");
		}
		validateQuantityBalance(request, workLabel);
		OrchidGroup source = orchidGroupRepository.findAllForUpdateByIdIn(List.of(request.sourceOrchidGroupId()))
				.stream().findFirst()
				.orElseThrow(() -> new NotFoundException("원본 난 묶음을 찾을 수 없습니다."));
		if (source.getVariety() == null) {
			throw new IllegalArgumentException("품종이 연결되지 않은 난 묶음은 " + workLabel + "할 수 없습니다.");
		}
		if (request.inputQuantity() > source.getQuantity()) {
			throw new IllegalArgumentException(workLabel + " 투입 수량은 현재 원본 수량보다 클 수 없습니다.");
		}
		Set<Long> collectionIds = validateInheritedCollections(source.getId(), request.inheritCollectionIds());
		Long varietyId = source.getVariety().getId();
		String resultStatus = source.getStatus();

		source.applyRepot(request.inputQuantity());
		List<OrchidGroup> results = request.results().stream().map(row -> {
			OrchidGroup result = orchidGroupCommandService.createEntity(new OrchidGroupCreateRequest(
					row.bedZoneId(), varietyId, row.quantity(), row.potSize(), row.ageYear(), resultStatus,
					row.placementType(), row.trayCount(), row.splitPlacementAllowed(),
					row.startPosition(), row.endPosition(), row.memo()));
			lineageService.record(
					source, result, relationType, operation, request.inputQuantity(), row.quantity());
			memberRepository.saveAll(collectionIds.stream()
					.map(id -> new OrchidGroupCollectionMember(id, result.getId(), command.worker()))
					.toList());
			return result;
		}).toList();

		var details = new LinkedHashMap<String, Object>();
		details.put("sourceOrchidGroupId", source.getId());
		details.put("inputQuantity", request.inputQuantity());
		details.put("remainingQuantity", source.getQuantity());
		details.put("lossQuantity", request.lossQuantity());
		if (request.lossReason() != null && !request.lossReason().isBlank()) {
			details.put("lossReason", request.lossReason().trim());
		}
		details.put("resultOrchidGroupIds", results.stream().map(OrchidGroup::getId).toList());
		return new WorkExecutionResult(handlerCode, details, results.stream().map(OrchidGroup::getId).toList());
	}

	private void validateQuantityBalance(RepotWorkOperationRequest request, String workLabel) {
		long resultQuantity = request.results().stream().mapToLong(row -> row.quantity()).sum();
		if (resultQuantity + request.lossQuantity() != request.inputQuantity()) {
			throw new IllegalArgumentException(workLabel + " 투입 수량은 결과 수량 합계와 손실 수량의 합과 같아야 합니다.");
		}
		if (request.lossQuantity() > 0
				&& (request.lossReason() == null || request.lossReason().isBlank())) {
			throw new IllegalArgumentException("손실 수량이 있으면 손실 사유를 입력해야 합니다.");
		}
	}

	private Set<Long> validateInheritedCollections(Long sourceId, Set<Long> requestedIds) {
		Set<Long> ids = requestedIds == null ? Set.of() : Set.copyOf(requestedIds);
		if (ids.isEmpty()) return ids;
		Set<Long> sourceCollectionIds = memberRepository
				.findByOrchidGroupIdAndRemovedAtIsNullOrderByJoinedAtAsc(sourceId).stream()
				.map(OrchidGroupCollectionMember::getCollectionId)
				.collect(Collectors.toSet());
		if (!sourceCollectionIds.containsAll(ids)) {
			throw new IllegalArgumentException("원본 난 묶음에 속한 사용자 그룹만 상속할 수 있습니다.");
		}
		List<OrchidGroupCollection> collections = collectionRepository.findAllById(ids);
		if (collections.size() != ids.size() || collections.stream().anyMatch(OrchidGroupCollection::isArchived)) {
			throw new IllegalArgumentException("활성 사용자 그룹만 상속할 수 있습니다.");
		}
		return ids;
	}
}
