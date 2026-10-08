package com.greenhouse.backend.work.correction.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.correction.StructureChangeMutationReferences;
import com.greenhouse.backend.work.api.correction.StructureChangeReferenceApi;
import com.greenhouse.backend.work.effect.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.effect.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StructureChangeReferenceReader implements StructureChangeReferenceApi {

  private final WorkOperationRepository workOperationRepository;

  private final WorkOperationTargetRepository workOperationTargetRepository;

  private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;

  @Override
  public Set<Long> getActiveOrchidGroupIds(Long workOperationId) {
    return workOperationTargetRepository
        .findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(workOperationId)
        .stream()
        .map(WorkOperationTarget::getOrchidGroupId)
        .collect(Collectors.toSet());
  }

  @Override
  public List<Long> getCorrectableResultOrchidGroupIds(Long operationId) {
    var operation =
        workOperationRepository
            .findWithWorkTypeById(operationId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
    if (!operation.isStructureResultCorrectable()) {
      throw new IllegalArgumentException("완료된 구조 변경 작업의 결과만 보정할 수 있습니다.");
    }
    return workEffectOrchidGroupRepository
        .findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId)
        .stream()
        .filter(link -> link.getRelationType() == WorkEffectOrchidGroupRelationType.RESULT)
        .map(link -> link.getOrchidGroupId())
        .distinct()
        .toList();
  }

  @Override
  public StructureChangeMutationReferences getMutationReferences(
      Long operationId, Set<Long> orchidGroupIds) {
    if (orchidGroupIds.isEmpty()) {
      return StructureChangeMutationReferences.legacy();
    }
    var links =
        workEffectOrchidGroupRepository
            .findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId)
            .stream()
            .filter(link -> link.getRelationType() == WorkEffectOrchidGroupRelationType.RESULT)
            .filter(link -> orchidGroupIds.contains(link.getOrchidGroupId()))
            .toList();
    Set<Long> linkedGroupIds =
        links.stream().map(link -> link.getOrchidGroupId()).collect(Collectors.toSet());
    if (!linkedGroupIds.containsAll(orchidGroupIds)
        || links.stream().anyMatch(link -> link.getWorkAppliedEffect().getMutationId() == null)) {
      return StructureChangeMutationReferences.legacy();
    }
    return StructureChangeMutationReferences.current(
        links.stream()
            .map(link -> link.getWorkAppliedEffect().getMutationId())
            .distinct()
            .sorted()
            .toList());
  }
}
