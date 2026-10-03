package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.dto.operation.WorkCorrectionAdjustmentResponse;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkExecutionReconciliationRow;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class WorkOrchidGroupLedgerRehearsalInspector {

	private final WorkTargetExecutionRepository executionRepository;

	private final WorkOperationTargetRepository targetRepository;

	private final WorkEffectOrchidGroupRepository effectGroupRepository;

	private final WorkAppliedEffectRepository appliedEffectRepository;

	private final WorkOperationCorrectionRepository correctionRepository;

	public WorkOrchidGroupLedgerRehearsalInspector(WorkTargetExecutionRepository executionRepository,
			WorkOperationTargetRepository targetRepository, WorkEffectOrchidGroupRepository effectGroupRepository,
			WorkAppliedEffectRepository appliedEffectRepository,
			WorkOperationCorrectionRepository correctionRepository) {
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

	private List<WorkOrchidGroupLedgerRehearsalReport.CorrectionReference> corrections() {
		var references = new ArrayList<WorkOrchidGroupLedgerRehearsalReport.CorrectionReference>();
		long afterId = Long.MIN_VALUE;
		while (true) {
			var rows = correctionRepository.findAfterId(afterId,
					PageRequest.of(0, 500));
			if (rows.isEmpty())
				return List.copyOf(references);
			for (var row : rows) {
				var detail = WorkCorrectionDetailResponse.from(row);
				var ids = detail.adjustments()
					.stream()
					.map(WorkCorrectionAdjustmentResponse::orchidGroupId)
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
