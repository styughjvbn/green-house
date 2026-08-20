package com.greenhouse.backend.farm.application.orchid.mutation;

import java.time.LocalDate;
import java.util.UUID;

public record OrchidGroupLedgerCutoverCommand(
		UUID cutoverKey,
		LocalDate effectiveBusinessDate,
		String minimumWriterVersion,
		String currentWriterVersion,
		boolean activate) {

	public OrchidGroupLedgerCutoverCommand {
		if (cutoverKey == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Cutover key와 적용 업무일이 필요합니다.");
		}
		minimumWriterVersion = requireText(minimumWriterVersion, "최소 writer version");
		currentWriterVersion = requireText(currentWriterVersion, "현재 writer version");
		if (!OrchidGroupLedgerWriterVersion.satisfiesMinimum(
				currentWriterVersion, minimumWriterVersion)) {
			throw new IllegalArgumentException("현재 writer version이 최소 writer version보다 낮습니다.");
		}
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "이 필요합니다.");
		}
		return value.trim();
	}
}
