package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrchidGroupLedgerCutoverService {

	private static final int BASELINE_BATCH_SIZE = 500;

	private final OrchidGroupLedgerReconciliationService reconciliationService;
	private final OrchidGroupLedgerPreparationService preparationService;
	private final OrchidGroupRepository orchidGroupRepository;

	public OrchidGroupLedgerCutoverResult execute(OrchidGroupLedgerCutoverCommand command) {
		OrchidGroupLedgerReconciliationReport initialReport = reconciliationService.reconcile();
		validateInitialState(command, initialReport);
		if (initialReport.stage() == OrchidGroupLedgerReconciliationStage.ACTIVE) {
			preparationService.validatePreparation(
					command.cutoverKey(),
					command.effectiveBusinessDate(),
					command.minimumWriterVersion());
			return result(command, 0, initialReport);
		}

		preparationService.prepare(
				command.cutoverKey(),
				command.effectiveBusinessDate(),
				command.minimumWriterVersion());
		preparationService.start(command.cutoverKey());

		int batchCount = 0;
		long afterId = 0L;
		while (true) {
			List<Long> groupIds = orchidGroupRepository.findIdsAfter(
					afterId, PageRequest.of(0, BASELINE_BATCH_SIZE));
			if (groupIds.isEmpty()) {
				break;
			}
			preparationService.baselineBatch(new BaselineOrchidGroupsCommand(
					command.cutoverKey(),
					batchKey(groupIds),
					groupIds,
					command.effectiveBusinessDate()));
			batchCount++;
			afterId = groupIds.getLast();
		}

		OrchidGroupLedgerReconciliationReport finalReport = reconciliationService.reconcile();
		if (!finalReport.ready()
				|| finalReport.stage() != OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING
				|| !command.cutoverKey().equals(finalReport.cutoverKey())) {
			throw new ConflictException("Baseline 적재 후 ledger 대사를 통과하지 못했습니다.");
		}
		if (command.activate()) {
			finalReport = preparationService.activate(
					command.cutoverKey(), command.currentWriterVersion());
		}
		return result(command, batchCount, finalReport);
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
			if (!report.ready()) {
				throw new ConflictException("Baseline 시작 전 운영 데이터 대사를 통과해야 합니다.");
			}
			return;
		}
		if (!command.cutoverKey().equals(report.cutoverKey())) {
			throw new ConflictException("다른 PREPARING coverage가 존재합니다.");
		}
		var unexpectedIssues = report.issues().stream()
				.filter(issue -> !"MISSING_LEDGER_CHAIN".equals(issue.code()))
				.toList();
		if (!unexpectedIssues.isEmpty()) {
			throw new ConflictException(
					"Baseline 재개 전에 해결할 대사 오류가 있습니다: "
							+ unexpectedIssues.getFirst().code());
		}
	}

	private String batchKey(List<Long> groupIds) {
		return "GROUPS:%020d-%020d".formatted(groupIds.getFirst(), groupIds.getLast());
	}

	private OrchidGroupLedgerCutoverResult result(
			OrchidGroupLedgerCutoverCommand command,
			int batchCount,
			OrchidGroupLedgerReconciliationReport report) {
		return new OrchidGroupLedgerCutoverResult(
				command.cutoverKey(),
				command.effectiveBusinessDate(),
				command.minimumWriterVersion(),
				command.currentWriterVersion(),
				batchCount,
				report.baselineGroupCount(),
				report.stage() == OrchidGroupLedgerReconciliationStage.ACTIVE,
				report);
	}
}
