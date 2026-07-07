package com.greenhouse.backend.sales.application;

final class SalesTextNormalizer {
	private SalesTextNormalizer() {
	}

	static String defaultText(String value, String defaultValue) {
		String normalized = normalize(value);
		return normalized == null ? defaultValue : normalized;
	}

	static String normalize(String value) {
		if (value == null)
			return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	static String required(String value) {
		String normalized = normalize(value);
		if (normalized == null)
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		return normalized;
	}
}
