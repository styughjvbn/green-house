package com.greenhouse.backend.settlement.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(
		name = "app.settlement.rebuild-on-startup",
		havingValue = "true",
		matchIfMissing = true)
@RequiredArgsConstructor
public class AuctionSettlementInitializer implements ApplicationRunner {
	private final AuctionSettlementService settlementService;

	@Override
	public void run(ApplicationArguments args) {
		settlementService.rebuildExistingResults();
	}
}
