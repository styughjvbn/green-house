package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrchidGroupStateChainMigrationCliTest {

	@Test
	void parsesOperatorOptionsAndForwardsOnlyApplicationArguments() {
		UUID cutoverKey = UUID.randomUUID();

		var parsed = OrchidGroupStateChainMigrationCli.parse(new String[] {
				"--cutover-key=" + cutoverKey,
				"--manifest=../scripts/data-audit/orchid-state-chain-migration-manifest.json",
				"--effective-business-date=2026-08-20",
				"--minimum-writer-version=1.0.0",
				"--apply=true",
				"--confirmation=IMPORT:" + cutoverKey,
				"--spring.datasource.url=jdbc:postgresql://localhost/test"
		});

		assertThat(parsed.cutoverKey()).isEqualTo(cutoverKey);
		assertThat(parsed.effectiveBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 20));
		assertThat(parsed.minimumWriterVersion()).isEqualTo("1.0.0");
		assertThat(parsed.apply()).isTrue();
		assertThat(parsed.manifestPath()).isAbsolute();
		assertThat(parsed.springArguments())
				.containsExactly("--spring.datasource.url=jdbc:postgresql://localhost/test");
	}

	@Test
	void requiresPlanConfirmationForReadOnlyValidation() {
		UUID cutoverKey = UUID.randomUUID();

		assertThatThrownBy(() -> OrchidGroupStateChainMigrationCli.parse(new String[] {
				"--cutover-key=" + cutoverKey,
				"--manifest=manifest.json",
				"--effective-business-date=2026-08-20",
				"--minimum-writer-version=1.0.0",
				"--apply=false",
				"--confirmation=IMPORT:" + cutoverKey
		}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("PLAN:" + cutoverKey);
	}

	@Test
	void rejectsMigrationSafetyOverrides() {
		UUID cutoverKey = UUID.randomUUID();

		assertThatThrownBy(() -> OrchidGroupStateChainMigrationCli.parse(new String[] {
				"--cutover-key=" + cutoverKey,
				"--manifest=manifest.json",
				"--effective-business-date=2026-08-20",
				"--minimum-writer-version=1.0.0",
				"--apply=false",
				"--confirmation=PLAN:" + cutoverKey,
				"--spring.flyway.enabled=true"
		}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("안전 옵션");
	}
}
