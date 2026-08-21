package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrchidGroupHistoryMigrationCliTest {

	@Test
	void parsesOperatorOptionsAndPreservesOnlySpringArguments() {
		UUID runKey = UUID.randomUUID();
		var parsed = OrchidGroupHistoryMigrationCli.parse(new String[] {
				"--run-key=" + runKey,
				"--source-cutoff=2026-08-21T03:00:01Z",
				"--backup-fingerprint=" + "a".repeat(64),
				"--manifest=../scripts/data-audit/orchid-history-migration-manifest.json",
				"--effective-business-date=2026-08-21",
				"--apply=false",
				"--confirmation=PLAN:" + runKey,
				"--spring.profiles.active=e2e"
		});

		assertThat(parsed.runKey()).isEqualTo(runKey);
		assertThat(parsed.sourceCutoff()).isEqualTo(Instant.parse("2026-08-21T03:00:01Z"));
		assertThat(parsed.effectiveBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 21));
		assertThat(parsed.apply()).isFalse();
		assertThat(parsed.springArguments()).containsExactly("--spring.profiles.active=e2e");
	}

	@Test
	void requiresModeSpecificConfirmationAndRejectsSafetyOverrides() {
		UUID runKey = UUID.randomUUID();
		String[] arguments = {
				"--run-key=" + runKey,
				"--source-cutoff=2026-08-21T03:00:01Z",
				"--backup-fingerprint=" + "a".repeat(64),
				"--manifest=manifest.json",
				"--effective-business-date=2026-08-21",
				"--apply=true",
				"--confirmation=PLAN:" + runKey
		};

		assertThatThrownBy(() -> OrchidGroupHistoryMigrationCli.parse(arguments))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("IMPORT:");
		assertThatThrownBy(() -> OrchidGroupHistoryMigrationCli.parse(new String[] {
				"--spring.datasource.hikari.read-only=true"
		}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("안전 옵션");
	}
}
