package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — coverage와 전환 writer mode/version 조합을 검사한다.
 * Removal gate: writer mode 제거 후 Engine 전용 기동 검증으로 대체.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(
		name = "app.orchid-ledger.startup-guard-enabled",
		havingValue = "true",
		matchIfMissing = true)
public class OrchidGroupLedgerWriterStartupGuard implements ApplicationRunner {

	private final OrchidGroupLedgerCoverageRepository coverageRepository;
	private final OrchidGroupLedgerWriterProperties properties;

	@Override
	public void run(ApplicationArguments args) {
		var activeCoverage = coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE);
		if (activeCoverage.isPresent()) {
			requireEngineWriter(activeCoverage.get(), "ACTIVE");
			return;
		}

		coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.PREPARING)
				.filter(coverage -> coverage.getImportStartedAt() != null)
				.ifPresent(coverage -> requireEngineWriter(coverage, "baseline이 시작된 PREPARING"));
	}

	private void requireEngineWriter(
			OrchidGroupLedgerCoverage coverage,
			String coverageState) {
		if (properties.writerMode() != OrchidGroupLedgerWriterMode.ENGINE) {
			throw new IllegalStateException(
					coverageState + " OrchidGroup ledger에는 ENGINE writer mode가 필요합니다.");
		}
		if (!OrchidGroupLedgerWriterVersion.satisfiesMinimum(
				properties.writerVersion(), coverage.getMinimumWriterVersion())) {
			throw new IllegalStateException(
					"현재 OrchidGroup writer version이 " + coverageState + " coverage의 최소 버전보다 낮습니다.");
		}
	}
}
