package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.orchid-ledger.startup-guard-enabled", havingValue = "true", matchIfMissing = true)
public class OrchidGroupLedgerWriterStartupGuard implements ApplicationRunner {

	private final OrchidGroupLedgerCoverageRepository coverageRepository;

	private final OrchidGroupLedgerWriterProperties properties;

	private final OrchidGroupRepository groupRepository;

	@Override
	public void run(ApplicationArguments args) {
		if (groupRepository.existsByStateRevisionIsNull()) {
			throw new IllegalStateException("상태 원장이 없는 난 묶음이 있습니다. 백업 복원과 state-chain 전환을 먼저 완료해야 합니다.");
		}
		var activeCoverage = coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE);
		if (activeCoverage.isPresent()) {
			requireWriterVersion(activeCoverage.get(), "ACTIVE");
			return;
		}

		if (coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.PREPARING).isPresent()) {
			throw new IllegalStateException("PREPARING 원장은 ACTIVE 전환을 먼저 완료해야 합니다.");
		}
	}

	private void requireWriterVersion(OrchidGroupLedgerCoverage coverage, String coverageState) {
		if (!OrchidGroupLedgerWriterVersion.satisfiesMinimum(properties.writerVersion(),
				coverage.getMinimumWriterVersion())) {
			throw new IllegalStateException(
					"현재 OrchidGroup writer version이 " + coverageState + " coverage의 최소 버전보다 낮습니다.");
		}
	}

}
