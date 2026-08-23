package com.greenhouse.backend.farm.application.orchid.mutation;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — LEGACY, SHADOW, ENGINE 전환 모드를 제공한다.
 * Removal gate: 모든 환경의 Engine 단일 writer 고정.
 */
@Validated
@ConfigurationProperties(prefix = "app.orchid-ledger")
public record OrchidGroupLedgerWriterProperties(
		OrchidGroupLedgerWriterMode writerMode,
		@NotBlank String writerVersion) {

	public OrchidGroupLedgerWriterProperties {
		if (writerMode == null) {
			writerMode = OrchidGroupLedgerWriterMode.LEGACY;
		}
		writerVersion = writerVersion == null ? null : writerVersion.trim();
	}

	public boolean routesToMutationEngine() {
		return writerMode == OrchidGroupLedgerWriterMode.ENGINE;
	}

	public boolean capturesShadowComparison() {
		return writerMode == OrchidGroupLedgerWriterMode.SHADOW;
	}

	public boolean usesMutationContract() {
		return writerMode != OrchidGroupLedgerWriterMode.LEGACY;
	}
}
