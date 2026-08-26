package com.greenhouse.backend.farm.application.orchid.mutation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — 요청별 Legacy/Engine 경로를 선택한다.
 * Removal gate: 모든 환경의 Engine 단일 writer 고정.
 */
@Component
@RequiredArgsConstructor
public class OrchidGroupMutationRoutingPolicy {

	private final OrchidGroupLedgerWriterProperties writerProperties;

	public boolean routesToEngine() {
		return writerProperties.routesToMutationEngine();
	}
}
