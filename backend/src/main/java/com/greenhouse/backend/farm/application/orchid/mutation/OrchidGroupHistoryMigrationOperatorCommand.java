package com.greenhouse.backend.farm.application.orchid.mutation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrchidGroupHistoryMigrationOperatorCommand(
		UUID runKey,
		Instant sourceCutoff,
		String backupFingerprint,
		String manifestFingerprint,
		LocalDate effectiveBusinessDate,
		boolean apply) {

	public OrchidGroupHistoryMigrationOperatorCommand {
		if (runKey == null || sourceCutoff == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Historical migration operator 필수 값이 누락되었습니다.");
		}
		if (backupFingerprint == null || !backupFingerprint.matches("[0-9a-f]{64}")
				|| manifestFingerprint == null || !manifestFingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Historical migration fingerprint 형식이 올바르지 않습니다.");
		}
	}
}
