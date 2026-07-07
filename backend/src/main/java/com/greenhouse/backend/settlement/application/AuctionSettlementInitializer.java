package com.greenhouse.backend.settlement.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuctionSettlementInitializer implements ApplicationRunner {
	private final AuctionSettlementService settlementService;

	@Override
	public void run(ApplicationArguments args) {
		settlementService.rebuildExistingResults();
	}
}
