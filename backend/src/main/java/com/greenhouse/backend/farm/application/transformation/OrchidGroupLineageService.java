package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageItemResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageNodeResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageTransformationResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.application.effect.StructureChangeLineageQueryService;
import java.time.Clock;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupLineageService {

	private final Clock clock;

	private final OrchidGroupLineageRepository lineageRepository;

	private final OrchidGroupRepository orchidGroupRepository;

	private final StructureChangeLineageQueryService structureChangeLineageQueryService;

	@Transactional
	public OrchidGroupLineage record(OrchidGroup source, OrchidGroup result,
			OrchidGroupLineageRelationType relationType, Long workOperationId, Integer sourceQuantity,
			Integer resultQuantity) {
		return lineageRepository.save(
				new OrchidGroupLineage(source, result, relationType, workOperationId, sourceQuantity, resultQuantity));
	}

	@Transactional(readOnly = true)
	public OrchidGroupLineageResponse getLineage(Long orchidGroupId) {
		var businessDate = TimeConfig.farmToday(clock);
		if (!orchidGroupRepository.existsById(orchidGroupId)) {
			throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
		}
		var transformationViews = structureChangeLineageQueryService.findByOrchidGroupId(orchidGroupId);
		var transformationGroupIds = transformationViews.stream()
			.flatMap(view -> java.util.stream.Stream.concat(view.sources().stream(), view.results().stream()))
			.map(group -> group.orchidGroupId())
			.collect(Collectors.toSet());
		var groupsById = orchidGroupRepository.findDetailsByIds(transformationGroupIds)
			.stream()
			.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
		var transformations = transformationViews.stream()
			.map(view -> new OrchidGroupLineageTransformationResponse(view.id(), relationType(view.handlerCode()),
					view.workOperationId(), view.sources().stream().mapToInt(group -> value(group.quantity())).sum(),
					view.results().stream().mapToInt(group -> value(group.quantity())).sum(), view.lossQuantity(),
					TimeConfig.toFarmTime(view.appliedAt()),
					view.sources()
						.stream()
						.map(group -> new OrchidGroupLineageNodeResponse(group.quantity(),
								OrchidGroupResponse.from(groupsById.get(group.orchidGroupId()), businessDate)))
						.toList(),
					view.results()
						.stream()
						.map(group -> new OrchidGroupLineageNodeResponse(group.quantity(),
								OrchidGroupResponse.from(groupsById.get(group.orchidGroupId()), businessDate)))
						.toList()))
			.toList();
		var pooledOperationIds = transformations.stream()
			.map(OrchidGroupLineageTransformationResponse::workOperationId)
			.collect(Collectors.toSet());
		var sources = lineageRepository.findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(orchidGroupId)
			.stream()
			.filter(lineage -> !pooledOperationIds.contains(lineage.getWorkOperationId()))
			.map(lineage -> OrchidGroupLineageItemResponse.from(lineage, businessDate))
			.toList();
		var results = lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(orchidGroupId)
			.stream()
			.filter(lineage -> !pooledOperationIds.contains(lineage.getWorkOperationId()))
			.map(lineage -> OrchidGroupLineageItemResponse.from(lineage, businessDate))
			.toList();
		return new OrchidGroupLineageResponse(orchidGroupId, sources, results, transformations);
	}

	private int value(Integer quantity) {
		return quantity == null ? 0 : quantity;
	}

	private OrchidGroupLineageRelationType relationType(String handlerCode) {
		return switch (handlerCode) {
			case "MOVEMENT" -> OrchidGroupLineageRelationType.MOVED_TO;
			case "REPOT" -> OrchidGroupLineageRelationType.REPOTTED_TO;
			case "DIVIDE" -> OrchidGroupLineageRelationType.SPLIT_TO;
			case "MERGE" -> OrchidGroupLineageRelationType.MERGED_TO;
			default -> throw new IllegalArgumentException("지원하지 않는 구조 변경 계보 유형입니다: " + handlerCode);
		};
	}

}
