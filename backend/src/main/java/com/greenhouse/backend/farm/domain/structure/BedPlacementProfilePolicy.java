package com.greenhouse.backend.farm.domain.structure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes and validates a complete replacement before any existing capacity is
 * removed.
 */
public final class BedPlacementProfilePolicy {

	private static final Set<String> FIXED_TYPES = Set.of("TRAY_12", "TRAY_15", "TRAY_20", "TRAY_24", "SINGLE_POT",
			"HANGING");

	private BedPlacementProfilePolicy() {
	}

	public static List<BedZoneCapacity> createCapacities(List<CapacityRule> rules) {
		var normalized = rules.stream().map(CapacityRule::normalized).toList();
		validate(normalized);
		return normalized.stream()
			.map(rule -> new BedZoneCapacity(rule.placementType(), rule.potSize(), rule.capacityMode(),
					rule.unitSpan().setScale(2, RoundingMode.HALF_UP), rule.capacityValue(), rule.allowed(),
					rule.memo()))
			.toList();
	}

	private static void validate(List<CapacityRule> rules) {
		Map<CapacityKey, Integer> previousByKey = new HashMap<>();
		Set<ModeKey> seen = new HashSet<>();
		for (var rule : rules.stream()
			.sorted(Comparator.comparingInt(value -> value.capacityMode().strength()))
			.toList()) {
			var key = new CapacityKey(rule.placementType(), rule.potSize());
			if (!seen.add(new ModeKey(key, rule.capacityMode()))) {
				throw new IllegalArgumentException("같은 수용 규칙이 중복되었습니다.");
			}
			if (rule.unitSpan().signum() <= 0) {
				throw new IllegalArgumentException("점유 폭은 0보다 커야 합니다.");
			}
			Integer previous = previousByKey.get(key);
			if (previous != null && rule.capacityValue() < previous) {
				throw new IllegalArgumentException("강한 배치 모드의 수용량은 이전 모드보다 작을 수 없습니다.");
			}
			previousByKey.put(key, rule.capacityValue());
		}
	}

	private static String normalizePlacementType(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		normalized = normalized.toUpperCase();
		if (!FIXED_TYPES.contains(normalized) && !normalized.startsWith("CUSTOM:")) {
			throw new IllegalArgumentException("지원하지 않는 배치 규격입니다.");
		}
		return normalized;
	}

	private static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public record CapacityRule(String placementType, String potSize, PlacementCapacityMode capacityMode,
			BigDecimal unitSpan, Integer capacityValue, Boolean allowed, String memo) {

		private CapacityRule normalized() {
			return new CapacityRule(normalizePlacementType(placementType), normalize(potSize), capacityMode, unitSpan,
					capacityValue, allowed, normalize(memo));
		}
	}

	private record CapacityKey(String placementType, String potSize) {
	}

	private record ModeKey(CapacityKey capacity, PlacementCapacityMode mode) {
	}

}
