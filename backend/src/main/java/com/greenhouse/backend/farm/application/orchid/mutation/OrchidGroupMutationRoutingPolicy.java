package com.greenhouse.backend.farm.application.orchid.mutation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrchidGroupMutationRoutingPolicy {

	private final OrchidGroupLedgerWriterProperties writerProperties;

	public boolean routesToEngine() {
		return writerProperties.routesToMutationEngine();
	}

	public boolean capturesShadowComparison() {
		return writerProperties.capturesShadowComparison();
	}

	public boolean usesMutationContract() {
		return writerProperties.usesMutationContract();
	}
}
