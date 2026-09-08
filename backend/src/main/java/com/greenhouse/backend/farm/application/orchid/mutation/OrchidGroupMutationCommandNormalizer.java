package com.greenhouse.backend.farm.application.orchid.mutation;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class OrchidGroupMutationCommandNormalizer {

	private OrchidGroupMutationCommandNormalizer() {
	}

	static BigDecimal normalizeNumber(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

	static String requireText(String value, String label) {
		String normalized = normalizeText(value);
		if (normalized == null) {
			throw new IllegalArgumentException(label + "가 필요합니다.");
		}
		return normalized;
	}

	static String normalizeText(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

}
