package com.greenhouse.backend.work.operation.application;

import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import com.greenhouse.backend.work.effect.web.dto.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.effect.web.dto.DiscardRecordResultRequest;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkType;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.target.web.dto.WorkTargetExecutionRequest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DiscardRecordService {

  private static final String MOVEMENT_DISCARD_REASON = "자리 이동 후 잔여 난 선별 폐기";

  private final WorkOperationPlanService planService;

  private final WorkOperationProgressService progressService;

  private final WorkTypeService workTypeService;

  private final WorkOperationTargetRepository targetRepository;

  private final WorkOperationQueryService queryService;

  private final WorkOperationSupport support;

  public List<WorkOperationView> create(DiscardRecordCreateRequest request) {
    return queryService.getAll(
        createOperations(request, null).stream().map(WorkOperation::getId).toList());
  }

  private List<WorkOperation> createOperations(
      DiscardRecordCreateRequest request, WorkOperation movementOperation) {
    List<WorkOperation> plannedOperations =
        planService.createBatchOperations(new WorkOperationBatchCreateRequest(request.operation()));
    if (plannedOperations.stream()
        .anyMatch(
            planned ->
                !WorkTypeDefinition.DISCARD.name().equals(planned.getWorkType().getCode()))) {
      throw new IllegalArgumentException("폐기 작업 기록만 이 방식으로 저장할 수 있습니다.");
    }
    Map<Long, DiscardRecordResultRequest> resultByGroupId =
        request.results().stream()
            .collect(
                Collectors.toMap(
                    DiscardRecordResultRequest::orchidGroupId,
                    Function.identity(),
                    (left, right) -> {
                      throw new IllegalArgumentException("폐기 결과의 난 묶음은 중복될 수 없습니다.");
                    },
                    LinkedHashMap::new));
    List<WorkOperationTarget> targets =
        targetRepository.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
            plannedOperations.stream().map(WorkOperation::getId).toList());
    Set<Long> plannedIds =
        targets.stream()
            .map(WorkOperationTarget::getOrchidGroupId)
            .collect(Collectors.toCollection(HashSet::new));
    if (!plannedIds.equals(resultByGroupId.keySet())) {
      throw new IllegalArgumentException("선택한 모든 난 묶음의 폐기 결과를 입력해야 합니다.");
    }

    Map<Long, List<WorkOperationTarget>> targetsByOperationId =
        targets.stream()
            .collect(Collectors.groupingBy(target -> target.getWorkOperation().getId()));
    for (WorkOperation planned : plannedOperations) {
      progressService.startOperation(planned.getId());
      for (var target : targetsByOperationId.get(planned.getId())) {
        DiscardRecordResultRequest result = resultByGroupId.get(target.getOrchidGroupId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("discardQuantity", result.discardQuantity());
        details.put("reason", normalize(result.reason()));
        progressService.completeTargetForRecord(
            planned.getId(),
            target.getId(),
            new WorkTargetExecutionRequest(request.worker(), details, request.completedDate()));
      }
    }
    if (movementOperation != null) {
      if (plannedOperations.size() != 1) {
        throw new IllegalStateException("자리 이동 연관 폐기는 하나의 품종별 작업이어야 합니다.");
      }
      WorkOperation discardOperation = plannedOperations.getFirst();
      discardOperation.updateTitle(
          support.followUpHistoryTitle(
              support.varietyHistoryTitle(
                  targets.getFirst().getVarietyNameSnapshot(), WorkTypeDefinition.MOVEMENT),
              "후 폐기"));
      discardOperation.linkToParent(movementOperation, WorkOperationRelationType.MOVEMENT_DISCARD);
    }
    return plannedOperations;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  Long createForMovement(
      WorkOperation movementOperation,
      LocalDate completedDate,
      String worker,
      String memo,
      Map<Long, Integer> inputQuantities,
      Map<Long, Integer> discardQuantities) {
    if (discardQuantities.isEmpty()) {
      return null;
    }
    WorkType discardType = workTypeService.getByCode(WorkTypeDefinition.DISCARD.name());
    List<Long> orchidGroupIds = discardQuantities.keySet().stream().sorted().toList();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("allocationMethod", "PROPORTIONAL_BY_INPUT_QUANTITY");
    details.put(
        "totalDiscardQuantity",
        discardQuantities.values().stream().mapToInt(Integer::intValue).sum());
    details.put("sourceInputQuantities", new LinkedHashMap<>(inputQuantities));
    List<WorkOperation> createdOperations =
        createOperations(
            new DiscardRecordCreateRequest(
                new WorkOperationCreateRequest(
                    discardType.getId(),
                    movementOperation.getTitle() + " - 이동 후 잔여 난 폐기",
                    completedDate,
                    completedDate,
                    WorkTargetSelection.manualSelection(orchidGroupIds),
                    details,
                    worker,
                    memo,
                    List.of()),
                completedDate,
                worker,
                orchidGroupIds.stream()
                    .map(
                        groupId ->
                            new DiscardRecordResultRequest(
                                groupId, discardQuantities.get(groupId), MOVEMENT_DISCARD_REASON))
                    .toList()),
            movementOperation);
    return createdOperations.getFirst().getId();
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
