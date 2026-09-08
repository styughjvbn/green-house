package com.greenhouse.backend.farm.application.orchid.mutation;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.orchid-ledger")
public record OrchidGroupLedgerWriterProperties(@NotBlank String writerVersion) {
	public OrchidGroupLedgerWriterProperties {
		writerVersion = writerVersion == null ? null : writerVersion.trim();
	}
}
