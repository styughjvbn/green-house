package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.api.orchid.ReconcileOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshotFactory;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkEffectResults;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.api.effect.WorkMutationLink;
import com.greenhouse.backend.work.api.effect.WorkReconciliationCommand;
import com.greenhouse.backend.work.spi.effect.WorkEffectHandler;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationWorkHandler implements WorkEffectHandler {

  private final OrchidGroupMutationEngine mutationEngine;

  private final OrchidGroupRepository orchidGroupRepository;

  @Override
  public String supports() {
    return "RECONCILIATION";
  }

  @Override
  public WorkEffectKind effectKind() {
    return WorkEffectKind.ATTRIBUTE_CHANGE;
  }

  @Override
  public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
    if (context.target() == null || context.target().orchidGroupId() == null) {
      throw new IllegalArgumentException("현장 동기화에는 난 묶음 대상이 필요합니다.");
    }
    WorkReconciliationCommand request = command.payloadAs(WorkReconciliationCommand.class);
    Long groupId = context.target().orchidGroupId();
    var before =
        orchidGroupRepository
            .findById(groupId)
            .map(OrchidGroupStateSnapshotFactory::from)
            .orElseThrow(() -> new IllegalArgumentException("현장 동기화 대상 난 묶음을 찾을 수 없습니다."));
    var mutation =
        mutationEngine.reconcile(
            new ReconcileOrchidGroupMutationCommand(
                OrchidGroupMutationSources.work(context.operationId(), command.effectKey()),
                groupId,
                request.actualQuantity(),
                request.actualStatus(),
                request.actualBedZoneId(),
                request.actualStartPosition(),
                request.actualEndPosition(),
                request.workDate(),
                request.reason()));
    var after =
        orchidGroupRepository
            .findById(groupId)
            .map(OrchidGroupStateSnapshotFactory::from)
            .orElseThrow();
    var details = new LinkedHashMap<String, Object>();
    details.put("orchidGroupId", groupId);
    details.put("reason", request.reason().trim());
    details.put("before", before);
    details.put("after", after);
    return new WorkExecutionResult(
        "RECONCILIATION",
        new WorkEffectResults.Json(details),
        List.of(groupId),
        new WorkMutationLink(mutation.mutationId(), mutation.correlationId()));
  }
}
