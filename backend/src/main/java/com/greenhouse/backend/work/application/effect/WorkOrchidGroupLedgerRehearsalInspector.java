package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkExecutionReconciliationRow;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import org.springframework.stereotype.Component;

@Component
public class WorkOrchidGroupLedgerRehearsalInspector {

	private final WorkTargetExecutionRepository executionRepository;

	private final WorkOperationTargetRepository targetRepository;

	private final WorkEffectOrchidGroupRepository effectGroupRepository;

	private final WorkAppliedEffectRepository appliedEffectRepository;

	private final com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository correctionRepository;

	public WorkOrchidGroupLedgerRehearsalInspector(WorkTargetExecutionRepository executionRepository,
			WorkOperationTargetRepository targetRepository, WorkEffectOrchidGroupRepository effectGroupRepository,
			WorkAppliedEffectRepository appliedEffectRepository,
			com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository correctionRepository) {
		this.executionRepository = executionRepository;
		this.targetRepository = targetRepository;
		this.effectGroupRepository = effectGroupRepository;
		this.appliedEffectRepository = appliedEffectRepository;
		this.correctionRepository = correctionRepository;
	}

	public WorkOrchidGroupLedgerRehearsalReport inspect() {
		return new WorkOrchidGroupLedgerRehearsalReport(targetRepository.findDistinctOrchidGroupIds(),
				effectGroupRepository.findDistinctOrchidGroupIds(),
				executionRepository.findReconciliationRows()
					.stream()
					.filter(row -> !validExecution(row))
					.map(WorkExecutionReconciliationRow::executionId)
					.toList(),
				appliedEffectRepository.findIdsWithIncompleteMutationLink(), corrections());
	}

	private java.util.List<WorkOrchidGroupLedgerRehearsalReport.CorrectionReference> corrections() {
		var references = new java.util.ArrayList<WorkOrchidGroupLedgerRehearsalReport.CorrectionReference>();
		long afterId = Long.MIN_VALUE;
		while (true) {
			var rows = correctionRepository.findAfterId(afterId,
					org.springframework.data.domain.PageRequest.of(0, 500));
			if (rows.isEmpty())
				return java.util.List.copyOf(references);
			for (var row : rows) {
				var detail = com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse.from(row);
				var ids = detail.adjustments()
					.stream()
					.map(com.greenhouse.backend.work.dto.operation.WorkCorrectionAdjustmentResponse::orchidGroupId)
					.toList();
				references.add(new WorkOrchidGroupLedgerRehearsalReport.CorrectionReference(row.getId(),
						row.getMutationId(), row.getCorrelationId(), !ids.isEmpty(), ids));
			}
			afterId = rows.getLast().getId();
		}
	}

	private boolean validExecution(WorkExecutionReconciliationRow row) {
		if (row.plannedQuantity() == null || row.plannedQuantity() < 0 || row.processedQuantity() == null
				|| row.processedQuantity() < 0 || row.processedQuantity() > row.plannedQuantity()
				|| row.status() == null) {
			return false;
		}
		return switch (row.status()) {
			case PENDING, IN_PROGRESS -> row.processedQuantity() == 0 && row.effectAppliedAt() == null;
			case PARTIALLY_COMPLETED -> row.processedQuantity() > 0 && row.processedQuantity() < row.plannedQuantity()
					&& row.effectAppliedAt() != null;
			case COMPLETED -> row.processedQuantity().equals(row.plannedQuantity()) && row.effectAppliedAt() != null;
			case SKIPPED, CANCELED, FAILED -> true;
		};
	}

}
