package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageItemResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageNodeResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageResponse;
import com.greenhouse.backend.farm.dto.transformation.OrchidGroupLineageTransformationResponse;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.api.effect.StructureChangeLineageQueryApi;
import java.time.Clock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupLineageService {

  private final Clock clock;

  private final OrchidGroupLineageRepository lineageRepository;

  private final OrchidGroupRepository orchidGroupRepository;

  private final StructureChangeLineageQueryApi structureChangeLineageQueryService;

  private final StructureChangeStrategyRegistry strategyRegistry;

  @Transactional
  public OrchidGroupLineage record(
      OrchidGroup source,
      OrchidGroup result,
      OrchidGroupLineageRelationType relationType,
      Long workOperationId,
      Integer sourceQuantity,
      Integer resultQuantity) {
    return lineageRepository.save(
        new OrchidGroupLineage(
            source, result, relationType, workOperationId, sourceQuantity, resultQuantity));
  }

  @Transactional(readOnly = true)
  public OrchidGroupLineageResponse getLineage(Long orchidGroupId) {
    var businessDate = TimeConfig.farmToday(clock);
    if (!orchidGroupRepository.existsById(orchidGroupId)) {
      throw new NotFoundException("난 묶음을 찾을 수 없습니다.");
    }
    var transformationViews = structureChangeLineageQueryService.findByOrchidGroupId(orchidGroupId);
    var pooledOperationIds =
        transformationViews.stream()
            .map(view -> view.workOperationId())
            .collect(Collectors.toSet());
    var sourceLinks =
        lineageRepository.findByResultOrchidGroupIdOrderByCreatedAtAscIdAsc(orchidGroupId).stream()
            .filter(lineage -> !pooledOperationIds.contains(lineage.getWorkOperationId()))
            .toList();
    var resultLinks =
        lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(orchidGroupId).stream()
            .filter(lineage -> !pooledOperationIds.contains(lineage.getWorkOperationId()))
            .toList();
    var groupIds =
        transformationViews.stream()
            .flatMap(view -> Stream.concat(view.sources().stream(), view.results().stream()))
            .map(group -> group.orchidGroupId())
            .collect(Collectors.toSet());
    Stream.concat(sourceLinks.stream(), resultLinks.stream())
        .forEach(
            lineage -> {
              groupIds.add(lineage.getSourceOrchidGroup().getId());
              groupIds.add(lineage.getResultOrchidGroup().getId());
            });
    // Also hydrate the managed groups referenced by direct links before their DTO mapper runs.
    var groupsById =
        orchidGroupRepository.findDetailsInBatches(groupIds).stream()
            .collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
    var transformations =
        transformationViews.stream()
            .map(
                view ->
                    new OrchidGroupLineageTransformationResponse(
                        view.id(),
                        strategyRegistry.get(view.structureTypeCode()).lineageType(),
                        view.workOperationId(),
                        view.sources().stream().mapToInt(group -> value(group.quantity())).sum(),
                        view.results().stream().mapToInt(group -> value(group.quantity())).sum(),
                        view.lossQuantity(),
                        view.increaseQuantity(),
                        TimeConfig.toFarmTime(view.appliedAt()),
                        view.sources().stream()
                            .map(
                                group ->
                                    new OrchidGroupLineageNodeResponse(
                                        group.quantity(),
                                        OrchidGroupResponse.from(
                                            groupsById.get(group.orchidGroupId()), businessDate)))
                            .toList(),
                        view.results().stream()
                            .map(
                                group ->
                                    new OrchidGroupLineageNodeResponse(
                                        group.quantity(),
                                        OrchidGroupResponse.from(
                                            groupsById.get(group.orchidGroupId()), businessDate)))
                            .toList()))
            .toList();
    var sources =
        sourceLinks.stream()
            .map(lineage -> OrchidGroupLineageItemResponse.from(lineage, businessDate))
            .toList();
    var results =
        resultLinks.stream()
            .map(lineage -> OrchidGroupLineageItemResponse.from(lineage, businessDate))
            .toList();
    return new OrchidGroupLineageResponse(orchidGroupId, sources, results, transformations);
  }

  private int value(Integer quantity) {
    return quantity == null ? 0 : quantity;
  }
}
