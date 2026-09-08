package com.greenhouse.backend.farm.application.orchid.mutation;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OrchidGroupLedgerWriterVersion {

	private static final Pattern SEMANTIC_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

	private OrchidGroupLedgerWriterVersion() {
	}

	static boolean satisfiesMinimum(String currentVersion, String minimumVersion) {
		String current = normalize(currentVersion);
		String minimum = normalize(minimumVersion);
		if (current.equals(minimum)) {
			return true;
		}
		Matcher currentMatcher = SEMANTIC_VERSION.matcher(current);
		Matcher minimumMatcher = SEMANTIC_VERSION.matcher(minimum);
		if (!currentMatcher.matches() || !minimumMatcher.matches()) {
			return false;
		}
		for (int group = 1; group <= 3; group++) {
			int comparison = new BigInteger(currentMatcher.group(group))
				.compareTo(new BigInteger(minimumMatcher.group(group)));
			if (comparison != 0) {
				return comparison > 0;
			}
		}
		return true;
	}

	private static String normalize(String version) {
		if (version == null || version.isBlank()) {
			throw new IllegalArgumentException("OrchidGroup writer version이 필요합니다.");
		}
		return version.trim();
	}

}
