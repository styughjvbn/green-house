package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WorkOperationLockService {

  private final WorkOperationRepository operationRepository;

  private final WorkOperationTargetRepository targetRepository;

  private final InboundPottingPlanGateway inboundGateway;

  public WorkOperation lock(Long operationId) {
    return lockAll(List.of(operationId)).getFirst();
  }

  public List<WorkOperation> lockAll(Collection<Long> operationIds) {
    var ids = operationIds.stream().distinct().sorted().toList();
    if (ids.isEmpty()) return List.of();
    var inboundIds = targetRepository.findInboundRecordIdsIn(ids);
    if (!inboundIds.isEmpty()) {
      inboundGateway.lockForPottingExecution(inboundIds);
    }
    var operations = operationRepository.findAllForUpdateByIdIn(ids);
    if (operations.size() != ids.size()) throw new NotFoundException("작업을 찾을 수 없습니다.");
    return operations;
  }

  public void lockInboundPlans(List<Long> requestedIds) {
    var statuses =
        Set.of(
            WorkOperationStatus.PLANNED,
            WorkOperationStatus.IN_PROGRESS,
            WorkOperationStatus.PAUSED);
    var operationIds = targetRepository.findActivePottingOperationIds(requestedIds, statuses);
    var siblingIds =
        operationIds.isEmpty()
            ? List.<Long>of()
            : targetRepository.findInboundRecordIdsIn(operationIds);
    var inboundIds =
        Stream.concat(requestedIds.stream(), siblingIds.stream()).distinct().sorted().toList();
    inboundGateway.lockForPottingExecution(inboundIds);
    if (!operationIds.equals(
        targetRepository.findActivePottingOperationIds(requestedIds, statuses))) {
      throw new ConflictException("WORK_PLAN_CHANGED", "포트 작업 계획이 변경되었습니다. 최신 상태로 다시 요청해주세요.");
    }
    if (!operationIds.isEmpty()) operationRepository.findAllForUpdateByIdIn(operationIds);
  }
}
