package com.greenhouse.backend.settlement.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuctionSettlementInitializer implements ApplicationRunner {
	private final AuctionSettlementService settlementService;

	public AuctionSettlementInitializer(AuctionSettlementService settlementService) {
		this.settlementService = settlementService;
	}

	@Override
	public void run(ApplicationArguments args) {
		settlementService.rebuildExistingResults();
	}
}
