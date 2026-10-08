package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupReconciliationRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.api.effect.WorkReconciliationCommand;
import com.greenhouse.backend.work.application.operation.ImmediateWorkExecutionService;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupReconciliationService {

  private final ImmediateWorkExecutionService immediateWorkExecutionService;

  private final OrchidGroupRepository orchidGroupRepository;

  public WorkOperationView reconcile(Long orchidGroupId, OrchidGroupReconciliationRequest request) {
    var orchidGroup =
        orchidGroupRepository
            .findDetailById(orchidGroupId)
            .orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
    return immediateWorkExecutionService.executeVarietyHistoryForTarget(
        request.idempotencyKey(),
        WorkTypeDefinition.RECONCILIATION.name(),
        orchidGroup.getVarietyName(),
        request.workDate(),
        request.worker(),
        request.memo(),
        orchidGroupId,
        Map.of("reason", request.reason().trim()),
        new WorkReconciliationCommand(
            request.idempotencyKey(),
            request.title(),
            request.workDate(),
            request.worker(),
            request.memo(),
            request.reason(),
            request.actualQuantity(),
            request.actualStatus(),
            request.actualBedZoneId(),
            request.actualStartPosition(),
            request.actualEndPosition()));
  }
}
