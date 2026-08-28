package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — complete state-chain 적재 확인과 ACTIVE 전환을 조율한다.
 * Removal gate: 운영 cutover 완료 및 재수행 불필요 승인.
 */
@Service
@RequiredArgsConstructor
public class OrchidGroupLedgerCutoverService {

	private final OrchidGroupLedgerReconciliationService reconciliationService;
	private final OrchidGroupLedgerPreparationService preparationService;

	public OrchidGroupLedgerCutoverResult execute(OrchidGroupLedgerCutoverCommand command) {
		OrchidGroupLedgerReconciliationReport initialReport = reconciliationService.reconcile();
		validateInitialState(command, initialReport);
		if (initialReport.stage() == OrchidGroupLedgerReconciliationStage.ACTIVE) {
			preparationService.validatePreparation(
					command.cutoverKey(),
					command.effectiveBusinessDate(),
					command.minimumWriterVersion());
			return result(command, initialReport);
		}

		OrchidGroupLedgerReconciliationReport finalReport = reconciliationService.reconcile();
		if (!finalReport.ready()
				|| finalReport.stage() != OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING
				|| !command.cutoverKey().equals(finalReport.cutoverKey())) {
			throw new ConflictException("Complete state-chain 적재 후 ledger 대사를 통과하지 못했습니다.");
		}
		preparationService.validateStateChainImported(command.cutoverKey());
		if (command.activate()) {
			finalReport = preparationService.activate(
					command.cutoverKey(), command.currentWriterVersion());
		}
		return result(command, finalReport);
	}

	private void validateInitialState(
			OrchidGroupLedgerCutoverCommand command,
			OrchidGroupLedgerReconciliationReport report) {
		if (report.stage() == OrchidGroupLedgerReconciliationStage.ACTIVE) {
			if (!command.cutoverKey().equals(report.cutoverKey()) || !report.ready()) {
				throw new ConflictException("다른 ACTIVE coverage가 존재하거나 ledger 대사가 실패했습니다.");
			}
			return;
		}
		if (report.stage() == OrchidGroupLedgerReconciliationStage.PRE_BASELINE) {
			throw new ConflictException("Cutover 전에 complete state-chain manifest를 적재해야 합니다.");
		}
		if (!command.cutoverKey().equals(report.cutoverKey())) {
			throw new ConflictException("다른 PREPARING coverage가 존재합니다.");
		}
		if (!report.ready()) {
			throw new ConflictException(
					"Cutover 전에 해결할 대사 오류가 있습니다: "
							+ report.issues().getFirst().code());
		}
		preparationService.validateStateChainImported(command.cutoverKey());
	}

	private OrchidGroupLedgerCutoverResult result(
			OrchidGroupLedgerCutoverCommand command,
			OrchidGroupLedgerReconciliationReport report) {
		return new OrchidGroupLedgerCutoverResult(
				command.cutoverKey(),
				command.effectiveBusinessDate(),
				command.minimumWriterVersion(),
				command.currentWriterVersion(),
				report.baselineGroupCount(),
				report.stage() == OrchidGroupLedgerReconciliationStage.ACTIVE,
				report);
	}
}
