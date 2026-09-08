package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupWriteFenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — coverage 준비와 최초 활성화를 수행한다. Removal gate: 운영 cutover
 * 완료 및 재수행 불필요 승인.
 */
@Service
@RequiredArgsConstructor
public class OrchidGroupLedgerPreparationService {

	private static final int ENGINE_SCHEMA_VERSION = 1;

	private static final int SNAPSHOT_SCHEMA_VERSION = 1;

	private final OrchidGroupLedgerCoverageRepository coverageRepository;

	private final OrchidGroupWriteFenceRepository writeFenceRepository;

	private final OrchidGroupLedgerReconciliationService reconciliationService;

	private final Clock clock;

	@Transactional
	public Long prepare(UUID cutoverKey, LocalDate effectiveBusinessDate, String minimumWriterVersion) {
		coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE).ifPresent(active -> {
			throw new ConflictException("이미 ACTIVE 상태인 OrchidGroup ledger coverage가 있습니다.");
		});
		return coverageRepository.findByCutoverKey(cutoverKey).map(existing -> {
			if (!existing.hasSamePreparation(ENGINE_SCHEMA_VERSION, SNAPSHOT_SCHEMA_VERSION, effectiveBusinessDate,
					minimumWriterVersion)) {
				throw new ConflictException("같은 cutover key를 다른 coverage 설정에 재사용할 수 없습니다.");
			}
			return existing.getId();
		}).orElseGet(() -> {
			coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.PREPARING).ifPresent(preparing -> {
				throw new ConflictException("다른 PREPARING OrchidGroup ledger coverage가 이미 있습니다.");
			});
			return coverageRepository
				.save(new OrchidGroupLedgerCoverage(cutoverKey, ENGINE_SCHEMA_VERSION, SNAPSHOT_SCHEMA_VERSION,
						effectiveBusinessDate, minimumWriterVersion))
				.getId();
		});
	}

	@Transactional(readOnly = true)
	public void validatePreparation(UUID cutoverKey, LocalDate effectiveBusinessDate, String minimumWriterVersion) {
		OrchidGroupLedgerCoverage coverage = findCoverage(cutoverKey);
		if (!coverage.hasSamePreparation(ENGINE_SCHEMA_VERSION, SNAPSHOT_SCHEMA_VERSION, effectiveBusinessDate,
				minimumWriterVersion)) {
			throw new ConflictException("Cutover command와 기존 coverage 설정이 다릅니다.");
		}
	}

	@Transactional(readOnly = true)
	public void validateStateChainImported(UUID cutoverKey) {
		OrchidGroupLedgerCoverage coverage = findCoverage(cutoverKey);
		if (coverage.getImportFingerprint() == null) {
			throw new ConflictException("Complete state-chain manifest 적재가 완료되지 않았습니다.");
		}
	}

	@Transactional
	public void startImport(UUID cutoverKey) {
		OrchidGroupLedgerCoverage coverage = findCoverage(cutoverKey);
		if (coverage.getStatus() != OrchidGroupLedgerCoverageStatus.PREPARING) {
			throw new IllegalStateException("PREPARING coverage만 state-chain 적재를 시작할 수 있습니다.");
		}
		if (coverage.getImportStartedAt() == null) {
			coverage.startImport(Instant.now(clock));
		}
	}

	@Transactional
	public OrchidGroupLedgerReconciliationReport activate(UUID cutoverKey, String currentWriterVersion) {
		OrchidGroupLedgerCoverage coverage = coverageRepository.findForUpdateByCutoverKey(cutoverKey)
			.orElseThrow(() -> new NotFoundException("OrchidGroup ledger coverage를 찾을 수 없습니다."));
		if (!OrchidGroupLedgerWriterVersion.satisfiesMinimum(currentWriterVersion,
				coverage.getMinimumWriterVersion())) {
			throw new ConflictException("현재 writer version이 coverage 최소 버전보다 낮습니다.");
		}
		writeFenceRepository.lockOrchidGroupsForCutover();
		OrchidGroupLedgerReconciliationReport report = reconciliationService.reconcile();
		if (!report.ready() || !cutoverKey.equals(report.cutoverKey())) {
			throw new ConflictException("대사를 통과한 동일 cutover coverage만 ACTIVE로 전환할 수 있습니다.");
		}
		if (coverage.getStatus() == OrchidGroupLedgerCoverageStatus.ACTIVE) {
			return report;
		}
		if (coverage.getStatus() != OrchidGroupLedgerCoverageStatus.PREPARING) {
			throw new ConflictException("PREPARING coverage만 ACTIVE로 전환할 수 있습니다.");
		}
		coverage.activate(Instant.now(clock), report.baselineGroupCount(), report.baselineFingerprint());
		coverageRepository.flush();
		OrchidGroupLedgerReconciliationReport activeReport = reconciliationService.reconcile();
		if (!activeReport.ready() || activeReport.stage() != OrchidGroupLedgerReconciliationStage.ACTIVE) {
			throw new ConflictException("ACTIVE 전환 후 ledger 대사가 일치하지 않습니다.");
		}
		return activeReport;
	}

	private OrchidGroupLedgerCoverage findCoverage(UUID cutoverKey) {
		return coverageRepository.findByCutoverKey(cutoverKey)
			.orElseThrow(() -> new NotFoundException("OrchidGroup ledger coverage를 찾을 수 없습니다."));
	}

}
