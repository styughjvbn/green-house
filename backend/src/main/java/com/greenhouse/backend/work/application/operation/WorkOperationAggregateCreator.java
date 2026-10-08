package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.target.WorkTargetResolver;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetInclusionSource;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import com.greenhouse.backend.work.spi.target.InboundPottingPlanTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkOperationAggregateCreator {

  private final WorkOperationRepository operationRepository;

  private final WorkOperationTargetRepository targetRepository;

  private final WorkTargetExecutionRepository executionRepository;

  private final WorkOperationSupport support;

  private final WorkTargetResolver workTargetResolver;

  public WorkOperation createForOrchidGroups(
      WorkOperation operation,
      List<ResolvedWorkTarget> resolvedTargets,
      WorkTargetInclusionSource inclusionSource,
      Long inclusionSourceId) {
    workTargetResolver.lockAndValidateActive(
        resolvedTargets.stream().map(ResolvedWorkTarget::orchidGroupId).toList());
    operationRepository.save(operation);
    var includedAt = support.now();
    List<WorkOperationTarget> targets =
        targetRepository.saveAll(
            resolvedTargets.stream()
                .map(
                    group ->
                        new WorkOperationTarget(
                            operation,
                            group.orchidGroupId(),
                            inclusionSource,
                            inclusionSourceId,
                            group.varietyId(),
                            group.varietyName(),
                            group.ageYear(),
                            group.potSizeCode(),
                            group.potSize(),
                            group.quantity(),
                            group.location(),
                            includedAt))
                .toList());
    executionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
    return operation;
  }

  public WorkOperation createForInboundRecords(
      WorkOperation operation, List<InboundPottingPlanTarget> records) {
    operationRepository.save(operation);
    var includedAt = support.now();
    List<WorkOperationTarget> targets =
        targetRepository.saveAll(
            records.stream()
                .map(
                    record ->
                        WorkOperationTarget.inboundRecord(
                            operation,
                            record.id(),
                            record.varietyId(),
                            record.varietyName(),
                            record.currentQuantity(0),
                            record.potSize(),
                            inboundLocation(record),
                            includedAt))
                .toList());
    executionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
    return operation;
  }

  private Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
    Map<String, Object> location = new LinkedHashMap<>();
    location.put("tempLocation", inbound.tempLocation());
    location.put("pottingDueDate", inbound.pottingDueDate());
    return location;
  }
}
