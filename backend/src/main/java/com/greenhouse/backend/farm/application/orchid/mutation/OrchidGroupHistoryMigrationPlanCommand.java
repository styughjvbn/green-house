package com.greenhouse.backend.farm.application.orchid.mutation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record OrchidGroupHistoryMigrationPlanCommand(
		UUID runKey,
		Instant sourceCutoff,
		String backupFingerprint,
		String manifestFingerprint,
		LocalDate effectiveBusinessDate,
		Map<String, Long> sourceCounts,
		Map<String, Long> plannedCounts) {

	public static final String MUTATIONS = "MUTATIONS";
	public static final String ENTRIES = "ENTRIES";

	public OrchidGroupHistoryMigrationPlanCommand {
		if (runKey == null || sourceCutoff == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Historical migration plan 필수 값이 누락되었습니다.");
		}
		backupFingerprint = requireFingerprint(backupFingerprint, "백업");
		manifestFingerprint = requireFingerprint(manifestFingerprint, "manifest");
		sourceCounts = immutableCounts(sourceCounts, "source counts");
		plannedCounts = immutableCounts(plannedCounts, "planned counts");
		if (!plannedCounts.containsKey(MUTATIONS) || !plannedCounts.containsKey(ENTRIES)) {
			throw new IllegalArgumentException("planned counts에는 MUTATIONS와 ENTRIES가 필요합니다.");
		}
	}

	private static String requireFingerprint(String value, String label) {
		if (value == null || !value.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(label + " fingerprint 형식이 올바르지 않습니다.");
		}
		return value;
	}

	private static Map<String, Long> immutableCounts(Map<String, Long> values, String label) {
		if (values == null || values.entrySet().stream().anyMatch(entry ->
				entry.getKey() == null || entry.getKey().isBlank()
						|| entry.getValue() == null || entry.getValue() < 0)) {
			throw new IllegalArgumentException(label + "가 올바르지 않습니다.");
		}
		return Map.copyOf(new LinkedHashMap<>(values));
	}
}
