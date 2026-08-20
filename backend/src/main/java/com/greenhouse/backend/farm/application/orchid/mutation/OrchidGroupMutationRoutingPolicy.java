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
}
