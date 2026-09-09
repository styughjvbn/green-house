package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.BackendApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Verifies Hibernate schema validation and the production ledger startup guard without
 * HTTP.
 */
public final class OrchidGroupLedgerStartupVerificationCli {

	private OrchidGroupLedgerStartupVerificationCli() {
	}

	public static void main(String[] args) {
		System.setProperty("spring.flyway.enabled", "false");
		System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
		System.setProperty("app.settlement.rebuild-on-startup", "false");
		try (var context = new SpringApplicationBuilder(BackendApplication.class).web(WebApplicationType.NONE)
			.run(args)) {
			System.out.println("OrchidGroup ledger startup verification passed.");
		}
	}

}
