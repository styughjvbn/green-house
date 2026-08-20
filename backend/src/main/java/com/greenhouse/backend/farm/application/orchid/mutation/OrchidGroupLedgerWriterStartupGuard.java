package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
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
@ConditionalOnProperty(
		name = "app.orchid-ledger.startup-guard-enabled",
		havingValue = "true",
		matchIfMissing = true)
public class OrchidGroupLedgerWriterStartupGuard implements ApplicationRunner {

	private final OrchidGroupLedgerCoverageRepository coverageRepository;
	private final OrchidGroupLedgerWriterProperties properties;

	@Override
	public void run(ApplicationArguments args) {
		coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE)
				.ifPresent(coverage -> {
					if (properties.writerMode() != OrchidGroupLedgerWriterMode.ENGINE) {
						throw new IllegalStateException(
								"ACTIVE OrchidGroup ledger에는 ENGINE writer mode가 필요합니다.");
					}
					if (!OrchidGroupLedgerWriterVersion.satisfiesMinimum(
							properties.writerVersion(), coverage.getMinimumWriterVersion())) {
						throw new IllegalStateException(
								"현재 OrchidGroup writer version이 ACTIVE coverage의 최소 버전보다 낮습니다.");
					}
				});
	}
}
