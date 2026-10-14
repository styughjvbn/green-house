package com.greenhouse.backend.work.effect.application;

import com.greenhouse.backend.work.api.effect.WorkOrchidGroupLedgerRehearsalApi;
import com.greenhouse.backend.work.api.effect.WorkOrchidGroupLedgerRehearsalReport;
import com.greenhouse.backend.work.correction.application.WorkCorrectionResultDetails;
import com.greenhouse.backend.work.correction.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.effect.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.operation.web.dto.WorkCorrectionAdjustmentResponse;
import com.greenhouse.backend.work.target.repository.WorkExecutionReconciliationRow;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.target.repository.WorkTargetExecutionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class WorkOrchidGroupLedgerRehearsalInspector implements WorkOrchidGroupLedgerRehearsalApi {

  private final WorkTargetExecutionRepository executionRepository;

  private final WorkOperationTargetRepository targetRepository;

  private final WorkEffectOrchidGroupRepository effectGroupRepository;

  private final WorkAppliedEffectRepository appliedEffectRepository;

  private final WorkOperationCorrectionRepository correctionRepository;

  public WorkOrchidGroupLedgerRehearsalInspector(
      WorkTargetExecutionRepository executionRepository,
      WorkOperationTargetRepository targetRepository,
      WorkEffectOrchidGroupRepository effectGroupRepository,
      WorkAppliedEffectRepository appliedEffectRepository,
      WorkOperationCorrectionRepository correctionRepository) {
    this.executionRepository = executionRepository;
    this.targetRepository = targetRepository;
    this.effectGroupRepository = effectGroupRepository;
    this.appliedEffectRepository = appliedEffectRepository;
    this.correctionRepository = correctionRepository;
  }

  @Override
  public WorkOrchidGroupLedgerRehearsalReport inspect() {
    return new WorkOrchidGroupLedgerRehearsalReport(
        targetRepository.findDistinctOrchidGroupIds(),
        effectGroupRepository.findDistinctOrchidGroupIds(),
        executionRepository.findReconciliationRows().stream()
            .filter(row -> !validExecution(row))
            .map(WorkExecutionReconciliationRow::executionId)
            .toList(),
        appliedEffectRepository.findIdsWithIncompleteMutationLink(),
        corrections());
  }

  private List<WorkOrchidGroupLedgerRehearsalReport.CorrectionReference> corrections() {
    var references = new ArrayList<WorkOrchidGroupLedgerRehearsalReport.CorrectionReference>();
    long afterId = Long.MIN_VALUE;
    while (true) {
      var rows =
          correctionRepository.findReconciliationRowsAfterId(afterId, PageRequest.of(0, 500));
      if (rows.isEmpty()) return List.copyOf(references);
      for (var row : rows) {
        var detail = WorkCorrectionResultDetails.from(row.resultDetails());
        var ids =
            detail.adjustments().stream()
                .map(WorkCorrectionAdjustmentResponse::orchidGroupId)
                .toList();
        references.add(
            new WorkOrchidGroupLedgerRehearsalReport.CorrectionReference(
                row.id(), row.mutationId(), row.correlationId(), !ids.isEmpty(), ids));
      }
      afterId = rows.getLast().id();
    }
  }

  private boolean validExecution(WorkExecutionReconciliationRow row) {
    if (row.plannedQuantity() == null
        || row.plannedQuantity() < 0
        || row.processedQuantity() == null
        || row.processedQuantity() < 0
        || row.processedQuantity() > row.plannedQuantity()
        || row.status() == null) {
      return false;
    }
    return switch (row.status()) {
      case PENDING, IN_PROGRESS -> row.processedQuantity() == 0 && row.effectAppliedAt() == null;
      case PARTIALLY_COMPLETED ->
          row.processedQuantity() > 0
              && row.processedQuantity() < row.plannedQuantity()
              && row.effectAppliedAt() != null;
      case COMPLETED ->
          row.processedQuantity().equals(row.plannedQuantity()) && row.effectAppliedAt() != null;
      case SKIPPED, CANCELED, FAILED -> true;
    };
  }
}
