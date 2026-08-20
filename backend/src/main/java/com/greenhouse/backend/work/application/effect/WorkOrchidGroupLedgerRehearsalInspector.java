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

	public WorkOrchidGroupLedgerRehearsalInspector(
			WorkTargetExecutionRepository executionRepository,
			WorkOperationTargetRepository targetRepository,
			WorkEffectOrchidGroupRepository effectGroupRepository,
			WorkAppliedEffectRepository appliedEffectRepository) {
		this.executionRepository = executionRepository;
		this.targetRepository = targetRepository;
		this.effectGroupRepository = effectGroupRepository;
		this.appliedEffectRepository = appliedEffectRepository;
	}

	public WorkOrchidGroupLedgerRehearsalReport inspect() {
		return new WorkOrchidGroupLedgerRehearsalReport(
				targetRepository.findDistinctOrchidGroupIds(),
				effectGroupRepository.findDistinctOrchidGroupIds(),
				executionRepository.findReconciliationRows().stream()
						.filter(row -> !validExecution(row))
						.map(WorkExecutionReconciliationRow::executionId)
						.toList(),
				appliedEffectRepository.findIdsWithIncompleteMutationLink());
	}

	private boolean validExecution(WorkExecutionReconciliationRow row) {
		if (row.plannedQuantity() == null || row.plannedQuantity() < 0
				|| row.processedQuantity() == null || row.processedQuantity() < 0
				|| row.processedQuantity() > row.plannedQuantity()
				|| row.status() == null) {
			return false;
		}
		return switch (row.status()) {
			case PENDING, IN_PROGRESS -> row.processedQuantity() == 0 && row.effectAppliedAt() == null;
			case PARTIALLY_COMPLETED -> row.processedQuantity() > 0
					&& row.processedQuantity() < row.plannedQuantity()
					&& row.effectAppliedAt() != null;
			case COMPLETED -> row.processedQuantity().equals(row.plannedQuantity())
					&& row.effectAppliedAt() != null;
			case SKIPPED, CANCELED, FAILED -> true;
		};
	}

}
