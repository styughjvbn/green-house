package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrchidGroupLedgerCutoverCliTest {

	@Test
	void parsesOperatorOptionsAndPassesOnlySpringOptionsToTheApplication() {
		UUID cutoverKey = UUID.randomUUID();

		var parsed = OrchidGroupLedgerCutoverCli.parse(new String[] {
				"--cutover-key=" + cutoverKey,
				"--effective-business-date=2026-08-20",
				"--minimum-writer-version=1.0.0",
				"--current-writer-version=1.1.0",
				"--activate=false",
				"--confirmation=BASELINE:" + cutoverKey,
				"--spring.profiles.active=e2e"
		});

		assertThat(parsed.command().cutoverKey()).isEqualTo(cutoverKey);
		assertThat(parsed.command().effectiveBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 20));
		assertThat(parsed.command().activate()).isFalse();
		assertThat(parsed.springArguments()).containsExactly("--spring.profiles.active=e2e");
	}

	@Test
	void requiresModeSpecificConfirmationAndRejectsSafetyOverrides() {
		UUID cutoverKey = UUID.randomUUID();
		String[] baseArguments = {
				"--cutover-key=" + cutoverKey,
				"--effective-business-date=2026-08-20",
				"--minimum-writer-version=1.0.0",
				"--current-writer-version=1.0.0",
				"--activate=true",
				"--confirmation=BASELINE:" + cutoverKey
		};

		assertThatThrownBy(() -> OrchidGroupLedgerCutoverCli.parse(baseArguments))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ACTIVATE:");
		assertThatThrownBy(() -> OrchidGroupLedgerCutoverCli.parse(new String[] {
				"--spring.flyway.enabled=true"
		}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("안전 옵션");
	}
}
